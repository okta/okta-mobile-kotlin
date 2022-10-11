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
package com.okta.nativeauthentication.form

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

internal class FormFactory(
    private val coroutineScope: CoroutineScope,
    private val sendChannel: SendChannel<Form>,
    private val formTransformers: List<FormTransformer>,
) {
    private val jobReference = AtomicReference<Job?>()
    private val previousEmission = AtomicReference<Form?>()

    suspend fun emit(formBuilder: Form.Builder, executeLaunchActions: Boolean = true) {
        val form = formBuilder.build(formTransformers)

        if (previousEmission.get() == form) {
            return
        }
        previousEmission.set(form)

        if (executeLaunchActions) {
            jobReference.getAndSet(null)?.cancel()
            if (formBuilder.launchActions.isNotEmpty()) {
                val job = coroutineScope.launch {
                    for (launchAction in formBuilder.launchActions) {
                        // Launch each action individually so they can run in parallel.
                        // These will be added as "children" to they'll all be cancelled with the parent job.
                        launch {
                            launchAction()
                        }
                    }
                }
                jobReference.set(job)
            }
        }

        sendChannel.send(form)
    }
}
