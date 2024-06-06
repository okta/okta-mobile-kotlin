/*
 * Copyright 2022-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.nativeauthentication

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.credential.Token
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.form.Form
import com.okta.testing.network.NetworkRule
import com.okta.testing.network.RequestMatchers.body
import com.okta.testing.network.RequestMatchers.path
import com.okta.testing.testBodyFromFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NativeAuthenticationClientTest {
    @get:Rule val networkRule = NetworkRule()

    private lateinit var fakeCallback: FakeCallback
    private lateinit var fakeIdxResponseTransformer: FakeIdxResponseTransformer

    private fun enqueueInteractSuccess() {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("SuccessInteractResponse.json")
        }
    }

    private fun enqueueInteractFailure() {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.setResponseCode(400)
        }
    }

    private suspend fun setup(
        interactResponseFactory: () -> Unit = ::enqueueInteractSuccess,
    ): Flow<Form> {
        interactResponseFactory()
        fakeCallback = FakeCallback()
        fakeIdxResponseTransformer = FakeIdxResponseTransformer()
        return NativeAuthenticationClient(emptyList(), fakeIdxResponseTransformer).create(fakeCallback) {
            InteractionCodeFlow.create("test.okta.com/login")
        }
    }

    @Test fun testNativeAuthenticationClientCreatesForm(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("IdentifyRemediationResponse.json")
        }
        val flow = setup()
        val forms = flow.take(2).toList()
        val loadingForm = forms[0]
        assertThat(loadingForm.elements).hasSize(1)
        assertThat(loadingForm.elements[0]).isInstanceOf(Element.Loading::class.java)
        val form = forms[1]
        val elements = form.elements
        assertThat(elements).hasSize(3)
        assertThat((elements[0] as Element.Label).text).isEqualTo("Fake Label")
        assertThat((elements[1] as Element.TextInput).label).isEqualTo("Fake Text Input")
        assertThat((elements[2] as Element.Action).text).isEqualTo("Fake Button")
    }

    @Test fun testNativeAuthenticationActionSendsRequest(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("IdentifyRemediationResponse.json")
        }
        networkRule.enqueue(
            path("/idp/idx/identify"),
            body("""{"identifier":"admin@example.com","credentials":{},"stateHandle":"029ZAB"}"""),
        ) { response ->
            response.testBodyFromFile("SuccessWithInteractionCodeResponse.json")
        }
        networkRule.enqueue(path("/oauth2/v1/token")) { response ->
            response.testBodyFromFile("TokenResponse.json")
        }
        val flow = setup()
        val formCounter = AtomicInteger()
        val forms = flow.onEach { form ->
            val elements = form.elements
            if (formCounter.getAndIncrement() == 1) {
                assertThat((elements[0] as Element.Label).text).isEqualTo("Fake Label")
                (elements[1] as Element.TextInput).value = "admin@example.com"
                (elements[2] as Element.Action).onClick(form)
            }
        }.toList()
        assertThat(forms).hasSize(4)
        assertThat(fakeCallback.tokens).hasSize(1)
    }

    @Test fun testCancellation(): Unit = runBlocking {
        val cancelCountDownLatch = CountDownLatch(1)
        val formReference = AtomicReference<Form>()
        val job = async(Dispatchers.IO) {
            networkRule.enqueue(path("/idp/idx/introspect")) { response ->
                response.testBodyFromFile("IdentifyRemediationResponse.json")
            }
            val flow = setup()
            flow.onEach { form ->
                formReference.set(form)
                if (form.elements[0] !is Element.Loading) {
                    cancelCountDownLatch.countDown()
                }
            }.collect()
        }
        assertThat(cancelCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue()
        job.cancel()

        // Implicitly ensuring this doesn't throw or cause another request.
        assertThat((formReference.get().elements[0] as Element.Label).text).isEqualTo("Fake Label")
        (formReference.get().elements[2] as Element.Action).onClick(formReference.get())
    }

    @Test fun testFailedInteractCallRetriesInteract(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("IdentifyRemediationResponse.json")
        }
        val flow = setup(::enqueueInteractFailure)
        val formCounter = AtomicInteger()
        val forms = flow.take(4).onEach { form ->
            if (formCounter.getAndIncrement() == 1) {
                enqueueInteractSuccess()
                (form.elements[0] as Element.Action).onClick(form)
            }
        }.toList()
        assertThat(forms[0].elements).hasSize(1)
        assertThat((forms[0].elements[0])).isInstanceOf(Element.Loading::class.java)
        assertThat(forms[1].elements).hasSize(1)
        assertThat((forms[1].elements[0] as Element.Action).text).isEqualTo("Retry")
        assertThat(forms[2].elements).hasSize(1)
        assertThat((forms[2].elements[0])).isInstanceOf(Element.Loading::class.java)
        assertThat(forms[3].elements).hasSize(3)
        assertThat((forms[3].elements[0] as Element.Label).text).isEqualTo("Fake Label")
        assertThat((forms[3].elements[1] as Element.TextInput).label).isEqualTo("Fake Text Input")
        assertThat((forms[3].elements[2] as Element.Action).text).isEqualTo("Fake Button")
    }

    @Test fun testFailedIntrospectCallRetriesIntrospect(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.setResponseCode(400)
        }
        val flow = setup()
        val formCounter = AtomicInteger()
        val forms = flow.take(4).onEach { form ->
            if (formCounter.getAndIncrement() == 1) {
                networkRule.enqueue(path("/idp/idx/introspect")) { response ->
                    response.testBodyFromFile("IdentifyRemediationResponse.json")
                }
                (form.elements[0] as Element.Action).onClick(form)
            }
        }.toList()
        assertThat(forms[0].elements).hasSize(1)
        assertThat(forms[0].elements[0]).isInstanceOf(Element.Loading::class.java)
        assertThat(forms[1].elements).hasSize(1)
        assertThat((forms[1].elements[0] as Element.Action).text).isEqualTo("Retry")
        assertThat(forms[2].elements).hasSize(1)
        assertThat(forms[2].elements[0]).isInstanceOf(Element.Loading::class.java)
        assertThat(forms[3].elements).hasSize(3)
        assertThat((forms[3].elements[0] as Element.Label).text).isEqualTo("Fake Label")
        assertThat((forms[3].elements[1] as Element.TextInput).label).isEqualTo("Fake Text Input")
        assertThat((forms[3].elements[2] as Element.Action).text).isEqualTo("Fake Button")
    }

    @Test fun testFailedIdentifyCallRetriesIdentify(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("IdentifyRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/identify")) { response ->
            response.setResponseCode(400)
        }
        networkRule.enqueue(path("/oauth2/v1/token")) { response ->
            response.testBodyFromFile("TokenResponse.json")
        }

        val flow = setup()
        val formCounter = AtomicInteger()
        val forms = flow.onEach { form ->
            val elements = form.elements
            when (formCounter.getAndIncrement()) {
                1 -> {
                    assertThat((elements[0] as Element.Label).text).isEqualTo("Fake Label")
                    (elements[2] as Element.Action).onClick(form)
                }
                3 -> {
                    networkRule.enqueue(path("/idp/idx/identify")) { response ->
                        response.testBodyFromFile("SuccessWithInteractionCodeResponse.json")
                    }
                    assertThat((elements[0] as Element.Action).text).isEqualTo("Retry")
                    (elements[0] as Element.Action).onClick(form)
                }
            }
        }.toList()
        assertThat(forms).hasSize(6)
    }

    @Test fun testFailedTokenCallRetriesToken(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("IdentifyRemediationResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/identify")) { response ->
            response.testBodyFromFile("SuccessWithInteractionCodeResponse.json")
        }
        networkRule.enqueue(path("/oauth2/v1/token")) { response ->
            response.setResponseCode(400)
        }

        val flow = setup()
        val formCounter = AtomicInteger()
        val forms = flow.onEach { form ->
            val elements = form.elements
            when (formCounter.getAndIncrement()) {
                1 -> {
                    assertThat((elements[0] as Element.Label).text).isEqualTo("Fake Label")
                    (elements[2] as Element.Action).onClick(form)
                }
                3 -> {
                    networkRule.enqueue(path("/oauth2/v1/token")) { response ->
                        response.testBodyFromFile("TokenResponse.json")
                    }
                    assertThat((elements[0] as Element.Action).text).isEqualTo("Retry")
                    (elements[0] as Element.Action).onClick(form)
                }
            }
        }.toList()
        assertThat(forms).hasSize(6)
    }

    @Test fun testNativeAuthenticationActionEmitsFormWithError(): Unit = runBlocking {
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("IdentifyRemediationResponse.json")
        }
        val flow = setup()
        fakeIdxResponseTransformer.makeTextInputRequired = true
        val formCounter = AtomicInteger()
        val forms = flow.onEach { form ->
            val elements = form.elements
            if (formCounter.getAndIncrement() == 1) {
                assertThat((elements[0] as Element.Label).text).isEqualTo("Fake Label")
                (elements[2] as Element.Action).onClick(form)
            }
        }.take(3).toList()
        val errorForm = forms[2]
        assertThat((errorForm.elements[1] as Element.TextInput).errorMessage).isEqualTo("Field is required.")
    }
}

private class FakeCallback : NativeAuthenticationClient.Callback() {
    private val _tokens = mutableListOf<Token>()
    val tokens: List<Token> = _tokens

    override fun signInComplete(token: Token) {
        super.signInComplete(token)
        _tokens += token
    }
}

private class FakeIdxResponseTransformer : IdxResponseTransformer {
    var makeTextInputRequired = false

    override fun transform(
        resultHandler: suspend (resultProducer: suspend (InteractionCodeFlow) -> OAuth2ClientResult<IdxResponse>) -> Unit,
        response: IdxResponse,
        clickHandler: (IdxRemediation, Form) -> Unit
    ): Form.Builder {
        val formBuilder = Form.Builder()

        val labelBuilder = Element.Label.Builder(remediation = null, text = "Fake Label")
        formBuilder.elements += labelBuilder

        response.remediations.firstOrNull()?.let { remediation ->
            remediation.form.visibleFields.firstOrNull()?.let { field ->
                val textInputBuilder = Element.TextInput.Builder(
                    remediation = remediation,
                    idxField = field,
                    label = "Fake Text Input",
                    value = "",
                    isSecret = false,
                    isRequired = makeTextInputRequired,
                    errorMessage = "",
                )
                formBuilder.elements += textInputBuilder
            }

            val actionBuilder = Element.Action.Builder(
                remediation = remediation,
                text = "Fake Button",
                onClick = { form ->
                    clickHandler(remediation, form)
                },
            )
            formBuilder.elements += actionBuilder
        }

        return formBuilder
    }
}
