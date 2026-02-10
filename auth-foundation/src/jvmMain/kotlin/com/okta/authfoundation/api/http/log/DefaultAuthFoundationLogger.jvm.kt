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
package com.okta.authfoundation.api.http.log

/**
 * Returns a default [AuthFoundationLogger] implementation for JVM that writes to standard output.
 */
actual fun getDefaultAuthFoundationLogger(): AuthFoundationLogger = JvmAuthFoundationLogger()

/**
 * A JVM implementation of [AuthFoundationLogger] that writes logs to standard output.
 *
 * @param tag The tag to prefix log messages with. Defaults to "AuthFoundation".
 */
class JvmAuthFoundationLogger(
    private val tag: String = "AuthFoundation",
) : AuthFoundationLogger {
    override fun write(
        message: String,
        tr: Throwable?,
        logLevel: LogLevel,
    ) {
        val level = logLevel.name.padEnd(5)
        println("$level [$tag] $message")
        tr?.printStackTrace()
    }
}
