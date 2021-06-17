package com.okta.idx.android.infrastructure

import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation

fun execShellCommand(cmd: String) {
    getInstrumentation().uiAutomation.executeShellCommand(cmd).close()
}
