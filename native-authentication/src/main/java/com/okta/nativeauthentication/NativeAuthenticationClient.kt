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
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.credential.Token
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.nativeauthentication.form.Form
import com.okta.nativeauthentication.form.FormFactory
import com.okta.nativeauthentication.form.FormTransformer
import com.okta.nativeauthentication.form.RetryFormBuilder
import com.okta.nativeauthentication.form.transformer.SignInTitleTransformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class NativeAuthenticationClient internal constructor(
    private val formTransformers: List<FormTransformer>,
    private val idxResponseTransformer: IdxResponseTransformer,
) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }

        fun create(
            callback: Callback,
            interactionCodeFlowFactory: suspend () -> OidcClientResult<InteractionCodeFlow>
        ): Flow<Form> {
            return NativeAuthenticationClient(
                formTransformers = listOf(SignInTitleTransformer()),
                idxResponseTransformer = RealIdxResponseTransformer(),
            ).create(callback, interactionCodeFlowFactory)
        }
    }

    abstract class Callback {
        open fun signInComplete(token: Token) {
        }
    }

    internal fun create(
        callback: Callback,
        interactionCodeFlowFactory: suspend () -> OidcClientResult<InteractionCodeFlow>,
    ): Flow<Form> {
        suspend fun start(formFactory: FormFactory, sendChannel: SendChannel<*>, coroutineScope: CoroutineScope) {
            when (val result = interactionCodeFlowFactory()) {
                is OidcClientResult.Error -> {
                    formFactory.emit(
                        RetryFormBuilder.create(coroutineScope) {
                            start(formFactory, sendChannel, coroutineScope)
                        }
                    )
                }
                is OidcClientResult.Success -> {
                    val interactionCodeFlow = result.result
                    val wrapperCallback = object : Callback() {
                        override fun signInComplete(token: Token) {
                            super.signInComplete(token)
                            callback.signInComplete(token)
                            sendChannel.close()
                        }
                    }
                    OidcClientResultTransformer(
                        callback = wrapperCallback,
                        interactionCodeFlow = interactionCodeFlow,
                        coroutineScope = coroutineScope,
                        formFactory = formFactory,
                        responseTransformer = idxResponseTransformer,
                    ).transformAndEmit {
                        interactionCodeFlow.resume()
                    }
                }
            }
        }

        return channelFlow {
            val formFactory = FormFactory(this, formTransformers)
            start(formFactory, this, this)
            awaitClose()
        }
    }
}
