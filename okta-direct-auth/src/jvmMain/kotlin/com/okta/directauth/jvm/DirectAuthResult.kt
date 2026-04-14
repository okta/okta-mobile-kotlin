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
package com.okta.directauth.jvm

/**
 * A Java-friendly result type that wraps a successful value or a failure exception.
 *
 * Unlike Kotlin's [kotlin.Result] (which is a value class with JVM limitations),
 * this class is a standard class that Java callers can use directly.
 *
 * @param T The type of the successful value.
 */
class DirectAuthResult<T> private constructor(
    private val value: T?,
    private val exception: Throwable?,
) {
    /**
     * Returns `true` if the result is a success.
     */
    fun isSuccess(): Boolean = exception == null

    /**
     * Returns `true` if the result is a failure.
     */
    fun isFailure(): Boolean = exception != null

    /**
     * Returns the successful value, or throws the failure exception.
     *
     * @return The successful value.
     * @throws Throwable if this result is a failure.
     */
    @Suppress("UNCHECKED_CAST")
    fun getOrThrow(): T {
        if (exception != null) throw exception
        return value as T
    }

    /**
     * Returns the successful value, or `null` if this result is a failure.
     */
    fun getOrNull(): T? = if (exception == null) value else null

    /**
     * Returns the failure exception, or `null` if this result is a success.
     */
    fun exceptionOrNull(): Throwable? = exception

    companion object {
        /**
         * Creates a successful result.
         */
        @JvmStatic
        fun <T> success(value: T): DirectAuthResult<T> = DirectAuthResult(value, null)

        /**
         * Creates a failed result.
         */
        @JvmStatic
        fun <T> failure(exception: Throwable): DirectAuthResult<T> = DirectAuthResult(null, exception)

        /**
         * Converts a Kotlin [kotlin.Result] to a Java-friendly [DirectAuthResult].
         */
        internal fun <T> fromKotlinResult(result: Result<T>): DirectAuthResult<T> =
            result.fold(
                onSuccess = { success(it) },
                onFailure = { failure(it) }
            )
    }
}
