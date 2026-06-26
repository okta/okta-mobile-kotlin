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
package com.okta.authfoundation.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CoalescingOrchestrator<T : Any>(
    private val factory: suspend () -> T,
    private val keepDataInMemory: (T) -> Boolean,
    // This should only be used for testing.
    private val awaitListener: (() -> Unit)? = null,
) {
    // Fast path: volatile read avoids lock on the common already-cached case.
    // @Volatile is meaningful on JVM/Android; a no-op on JS/WASM (harmless — single-threaded).
    @Volatile private var cachedData: T? = null

    // Always accessed under mutex.
    private var deferred: Deferred<T>? = null
    private val mutex = Mutex()

    suspend fun get(): T {
        cachedData?.let { return it }
        var result: T? = null
        while (result == null) {
            result = fetchOnce()
        }
        return result
    }

    private suspend fun fetchOnce(): T? =
        coroutineScope {
            val deferredToAwait: Deferred<T> =
                mutex.withLock {
                    cachedData?.let { return@coroutineScope it }
                    deferred?.takeIf { !it.isCancelled }
                        ?: async(start = CoroutineStart.LAZY) {
                            val result = factory()
                            mutex.withLock {
                                if (keepDataInMemory(result)) {
                                    cachedData = result
                                }
                                deferred = null
                            }
                            result
                        }.also { deferred = it }
                }

            try {
                awaitListener?.invoke()
                deferredToAwait.await()
            } catch (_: CancellationException) {
                // Re-throw if the calling coroutine itself was cancelled; otherwise the deferred
                // was cancelled by another caller and we should retry.
                currentCoroutineContext().ensureActive()
                null
            }
        }
}
