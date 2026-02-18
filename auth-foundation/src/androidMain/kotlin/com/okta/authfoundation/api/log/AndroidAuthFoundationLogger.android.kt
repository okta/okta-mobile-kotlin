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
package com.okta.authfoundation.api.log

import android.util.Log

/**
 * Returns a default [AuthFoundationLogger] implementation for Android that writes to Logcat.
 */
actual fun getDefaultAuthFoundationLogger(): AuthFoundationLogger = AndroidAuthFoundationLogger()

/**
 * An Android implementation of [AuthFoundationLogger] that writes logs to Android's Logcat.
 *
 * @param tag The tag to use for Logcat messages. Defaults to "AuthFoundation".
 */
class AndroidAuthFoundationLogger(
    private val tag: String = "AuthFoundation",
) : AuthFoundationLogger {
    override fun write(
        message: String,
        tr: Throwable?,
        logLevel: LogLevel,
    ) {
        when (logLevel) {
            LogLevel.DEBUG -> Log.d(tag, message, tr)
            LogLevel.INFO -> Log.i(tag, message, tr)
            LogLevel.WARN -> Log.w(tag, message, tr)
            LogLevel.ERROR -> Log.e(tag, message, tr)
        }
    }
}
