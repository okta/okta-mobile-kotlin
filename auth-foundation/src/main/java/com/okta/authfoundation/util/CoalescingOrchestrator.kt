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

import com.okta.authfoundation.InternalAuthFoundationApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@InternalAuthFoundationApi
class CoalescingOrchestrator<T : Any>(
    private val factory: suspend () -> T,
    private val keepDataInMemory: (T) -> Boolean,
    // This should only be used for testing.
    private val awaitListener: (() -> Unit)? = null,
) {
    @Volatile private lateinit var data: T
    @Volatile private var dataInitialized: Boolean = false

    @Volatile private var deferred: Deferred<T>? = null
    private val lock: Any = Any()

    tailrec suspend fun get(): T {
        if (dataInitialized) {
            return data
        }

        val result = coroutineScope {
            val deferredToAwait: Deferred<T>
            synchronized(lock) {
                if (dataInitialized) {
                    return@coroutineScope data
                }
                val localDeferred = deferred
                if (localDeferred != null && !localDeferred.isCancelled) {
                    deferredToAwait = localDeferred
                } else {
                    deferredToAwait = loadDataAsync(this@coroutineScope)
                }
            }
            try {
                awaitListener?.invoke()
                deferredToAwait.await()
            } catch (e: CancellationException) {
                // The `deferredToAwait` was cancelled before we could await it by another thread.
                null
            }
        }
        if (result != null) {
            return result
        }
        // Null returned due to cancellation. Try again by calling ourself.
        return get()
    }

    private fun loadDataAsync(scope: CoroutineScope): Deferred<T> {
        val local = scope.async(start = CoroutineStart.LAZY) {
            val result = factory()
            synchronized(lock) {
                if (keepDataInMemory(result)) {
                    data = result
                    dataInitialized = true
                }
                deferred = null
            }
            result
        }

        deferred = local

        return local
    }
}
