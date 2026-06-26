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
package com.okta.directauth.app.util

interface PlatformLogger {
    fun write(
        message: String,
        tr: Throwable? = null,
    )
}

expect fun getPlatformLogger(): PlatformLogger

internal object AppLogger {
    private val logger: PlatformLogger = getPlatformLogger()

    fun write(
        tag: String,
        message: String,
    ) {
        logger.write("$tag: $message")
    }
}

/**
 * Provides an implicit log tag so that [log] can be called without passing a tag explicitly.
 * Implement this on a class whose companion object or the class itself holds the tag constant.
 */
interface LogScope {
    val logTag: String
}

/**
 * Logs [message] prefixed with the tag from the ambient [LogScope].
 *
 * Usage — declare [cancelAndLaunch] (or similar scope) to accept
 * `suspend context(LogScope) () -> Unit` and provide the VM's [LogScope] via `context(this) { ... }`.
 * Inside the block every call to [log] resolves the tag automatically.
 */
context(scope: LogScope)
internal fun log(message: String) = AppLogger.write(scope.logTag, message)
