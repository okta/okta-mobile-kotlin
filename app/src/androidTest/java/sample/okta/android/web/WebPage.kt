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
package sample.okta.android.web

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import sample.okta.android.dashboard.DashboardPage
import sample.okta.android.test.clickButtonWithText
import sample.okta.android.test.clickButtonWithTextMatching
import sample.okta.android.test.execShellCommand
import sample.okta.android.test.setTextForIndex
import sample.okta.android.test.waitForText
import timber.log.Timber

internal class WebPage<PreviousPage>(
    private val previousPage: PreviousPage,
    initialText: String = "Sign in"
) {
    companion object {
        fun clearData() {
            execShellCommand("pm clear com.android.chrome")

            Thread.sleep(2000)

            val application = ApplicationProvider.getApplicationContext<Application>()
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://okta.com"))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            application.startActivity(browserIntent)

            try {
                clickButtonWithText("Accept & continue")
            } catch (e: Throwable) {
                Timber.e(e, "Error Calling accept and continue")
            }

            try {
                clickButtonWithTextMatching("No [t|T]hanks")
            } catch (e: Throwable) {
                Timber.e(e, "Error Calling No thanks")
            }

            Thread.sleep(2000)
            execShellCommand("am force-stop com.android.chrome")
            Thread.sleep(2000)
        }

        fun launch(url: String, initialText: String): WebPage<Any?> {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ApplicationProvider.getApplicationContext<Context>().startActivity(intent)
            return WebPage(null, initialText)
        }
    }

    init {
        waitForText(initialText)
    }

    fun username(username: String): WebPage<PreviousPage> {
        setTextForIndex(0, username)
        return this
    }

    fun password(password: String): WebPage<PreviousPage> {
        setTextForIndex(1, password)
        return this
    }

    fun login(): DashboardPage {
        clickButtonWithText("Sign in")
        return DashboardPage()
    }

    fun loginExpectingError(): WebPage<PreviousPage> {
        clickButtonWithText("Sign in")
        return this
    }

    fun loginExpectingDeviceActivated(): WebPage<PreviousPage> {
        clickButtonWithText("Sign in")
        waitForText("Device activated")
        return this
    }

    fun cancel(): PreviousPage {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressBack()
        device.wait(Until.findObject(By.pkg("sample.okta.oidc.android")), 2_000)
        return previousPage
    }

    fun assertHasError(error: String): WebPage<PreviousPage> {
        waitForText(error)
        return this
    }

    fun enterCode(code: String): WebPage<PreviousPage> {
        setTextForIndex(0, code)
        return this
    }

    fun clickNext(): WebPage<PreviousPage> {
        clickButtonWithText("Next")
        waitForText("Sign in")
        return this
    }
}
