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

import com.okta.authfoundation.client.OidcClientResult
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.dto.IdxIdpCapability
import com.okta.idx.kotlin.dto.IdxPollRemediationCapability
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.form.Form
import com.okta.nativeauthentication.form.FormFactory
import com.okta.nativeauthentication.form.LabelFormBuilder
import com.okta.nativeauthentication.form.RetryFormBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class IdxResponseTransformer(
    private val callback: NativeAuthenticationClient.Callback,
    private val interactionCodeFlow: InteractionCodeFlow,
    private val coroutineScope: CoroutineScope,
    private val formFactory: FormFactory,
) {
    suspend fun transformAndEmit(
        resultProducer: suspend () -> OidcClientResult<IdxResponse>,
    ) {
        when (val result = resultProducer()) {
            is OidcClientResult.Error -> {
                formFactory.emit(
                    RetryFormBuilder.create(coroutineScope) {
                        transformAndEmit(resultProducer)
                    }
                )
            }
            is OidcClientResult.Success -> {
                val response = result.result
                if (response.isLoginSuccessful) {
                    exchangeInteractionCodeForTokensAndEmit(response)
                } else {
                    val builder = Form.Builder()
                    for (remediation in response.remediations) {
                        for (field in remediation.form.visibleFields) {
                            builder.elements.addAll(field.asDynamicAuthFields(remediation))
                        }
                        builder.elements.addAll(remediation.asAction())
                    }
                    formFactory.emit(builder)
                }
            }
        }
    }

    private fun IdxRemediation.Form.Field.asDynamicAuthFields(remediation: IdxRemediation): List<Element.Builder<*>> {
        return when {
            // Nested form inside a field.
            !form?.visibleFields.isNullOrEmpty() -> {
                val result = mutableListOf<Element.Builder<*>>()
                form?.visibleFields?.forEach {
                    result += it.asDynamicAuthFields(remediation)
                }
                result
            }
            // Simple text field.
            (type == "string") -> {
                val field = Element.TextInput.Builder(remediation, this)
                field.label = label ?: ""
                field.isSecret = isSecret
                (value as? String?)?.let {
                    field.value = it
                }
                listOf(field)
            }
            else -> {
                emptyList()
            }
        }
    }

    private fun IdxRemediation.asAction(): List<Element.Builder<*>> {
        if (form.visibleFields.isEmpty() && capabilities.get<IdxPollRemediationCapability>() != null) {
            return emptyList()
        }

        val title = when (type) {
            IdxRemediation.Type.SKIP -> "Skip"
            IdxRemediation.Type.ENROLL_PROFILE, IdxRemediation.Type.SELECT_ENROLL_PROFILE -> "Sign Up"
            IdxRemediation.Type.SELECT_IDENTIFY, IdxRemediation.Type.IDENTIFY -> "Sign In"
            IdxRemediation.Type.SELECT_AUTHENTICATOR_AUTHENTICATE, IdxRemediation.Type.SELECT_AUTHENTICATOR_ENROLL -> "Choose"
            IdxRemediation.Type.LAUNCH_AUTHENTICATOR -> "Launch Authenticator"
            IdxRemediation.Type.CANCEL -> "Restart"
            IdxRemediation.Type.UNLOCK_ACCOUNT -> "Unlock Account"
            IdxRemediation.Type.REDIRECT_IDP -> {
                capabilities.get<IdxIdpCapability>()?.let { capability ->
                    "Login with ${capability.name}"
                } ?: "Social Login"
            }
            else -> "Continue"
        }

        val builder = Element.Action.Builder(this)
        builder.text = title
        builder.onClick = {
            coroutineScope.launch {
                transformAndEmit { interactionCodeFlow.proceed(this@asAction) }
            }
        }
        return listOf(builder)
    }

    private suspend fun exchangeInteractionCodeForTokensAndEmit(response: IdxResponse) {
        formFactory.emit(LabelFormBuilder.create("Loading"))

        val remediation = response.remediations[IdxRemediation.Type.ISSUE]!!
        when (val result = interactionCodeFlow.exchangeInteractionCodeForTokens(remediation)) {
            is OidcClientResult.Error -> {
                formFactory.emit(
                    RetryFormBuilder.create(coroutineScope) {
                        exchangeInteractionCodeForTokensAndEmit(response)
                    }
                )
            }
            is OidcClientResult.Success -> {
                formFactory.emit(LabelFormBuilder.create("Complete"))

                callback.signInComplete(result.result)
            }
        }
    }
}
