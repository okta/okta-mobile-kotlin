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
 * A simple logging interface used by the SDK to output diagnostic information.
 *
 * This abstraction allows consumers to integrate the SDK's logging with their
 * application's existing logging framework, such as Timber or a custom solution.
 */
interface AuthFoundationLogger {
    /**
     * Writes a log message with an optional throwable and a specified log level.
     *
     * @param message The log message to be printed.
     * @param tr An optional [Throwable] to be logged with the message. Defaults to null.
     * @param logLevel The severity of the log message. Defaults to [LogLevel.INFO].
     */
    fun write(
        message: String,
        tr: Throwable? = null,
        logLevel: LogLevel = LogLevel.INFO,
    )
}
