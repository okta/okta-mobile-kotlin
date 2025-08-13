package com.okta.directauth.log

import android.util.Log
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.api.http.log.LogLevel

class AuthFoundationLoggerImpl : AuthFoundationLogger {
    override fun write(message: String, tr: Throwable?, logLevel: LogLevel) {
        val tag = "DirectAuthentication"
        when (logLevel) {
            LogLevel.DEBUG -> Log.d(tag, message, tr)
            LogLevel.INFO -> Log.i(tag, message, tr)
            LogLevel.WARN -> Log.w(tag, message, tr)
            LogLevel.ERROR -> Log.e(tag, message, tr)
        }
    }
}
