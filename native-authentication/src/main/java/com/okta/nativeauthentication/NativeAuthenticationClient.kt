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
import com.okta.nativeauthentication.form.RetryFormBuilder
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class NativeAuthenticationClient private constructor() {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }

        fun create(
            callback: Callback,
            interactionCodeFlowFactory: suspend () -> OidcClientResult<InteractionCodeFlow>,
        ): Flow<Form> {
            suspend fun ProducerScope<Form>.start() {
                when (val result = interactionCodeFlowFactory()) {
                    is OidcClientResult.Error -> {
                        RetryFormBuilder.emit(this) {
                            start()
                        }
                    }
                    is OidcClientResult.Success -> {
                        val interactionCodeFlow = result.result
                        IdxResponseTransformer(callback, interactionCodeFlow, this).transformAndEmit {
                            interactionCodeFlow.resume()
                        }
                    }
                }
            }

            return channelFlow {
                start()
                awaitClose()
            }
        }
    }

    abstract class Callback {
        open fun signInComplete(token: Token) {
        }
    }
}
