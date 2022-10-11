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
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.nativeauthentication.form.FormFactory
import com.okta.nativeauthentication.form.LabelFormBuilder
import com.okta.nativeauthentication.form.LoadingFormBuilder
import com.okta.nativeauthentication.form.RetryFormBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OidcClientResultTransformer(
    private val callback: NativeAuthenticationClient.Callback,
    private val interactionCodeFlow: InteractionCodeFlow,
    private val coroutineScope: CoroutineScope,
    private val formFactory: FormFactory,
    private val responseTransformer: IdxResponseTransformer,
) {
    suspend fun transformAndEmit(
        resultProducer: suspend (InteractionCodeFlow) -> OidcClientResult<IdxResponse>,
    ) {
        formFactory.emit(LoadingFormBuilder.create())

        when (val result = resultProducer(interactionCodeFlow)) {
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
                    coroutineScope.launch {
                        exchangeInteractionCodeForTokensAndEmit(response)
                    }
                } else {
                    formFactory.emit(
                        responseTransformer.transform(::transformAndEmit, response) { remediation, form ->
                            coroutineScope.launch {
                                val (formIsValid, updatedForm) = form.validate(remediation)
                                if (formIsValid) {
                                    transformAndEmit {
                                        interactionCodeFlow.proceed(remediation)
                                    }
                                } else {
                                    formFactory.emit(updatedForm, executeLaunchActions = false)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private suspend fun exchangeInteractionCodeForTokensAndEmit(response: IdxResponse) {
        formFactory.emit(LoadingFormBuilder.create())

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
