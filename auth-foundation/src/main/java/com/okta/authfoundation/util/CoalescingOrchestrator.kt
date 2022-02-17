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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class CoalescingOrchestrator<T : Any>(
    private val factory: suspend () -> T,
    private val keepDataInMemory: (T) -> Boolean,
) {
    @Volatile private lateinit var data: T
    @Volatile private var dataInitialized: Boolean = false

    @Volatile private var deferred: Deferred<T>? = null
    private val lock: Any = Any()

    suspend fun get(): T {
        if (dataInitialized) {
            return data
        }

        return withContext(Dispatchers.Unconfined) {
            val deferredToAwait: Deferred<T>
            synchronized(lock) {
                if (dataInitialized) {
                    return@withContext data
                }
                val localDeferred = deferred
                if (localDeferred != null && localDeferred.isActive) {
                    deferredToAwait = localDeferred
                } else {
                    deferredToAwait = loadDataAsync(this@withContext)
                }
            }
            deferredToAwait.await()
        }
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
