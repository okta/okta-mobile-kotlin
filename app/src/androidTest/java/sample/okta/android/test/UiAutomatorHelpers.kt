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
package sample.okta.android.test

import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.google.common.truth.Truth.assertThat

fun clickButtonWithText(text: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().text(text)
    assertThat(uiDevice.findObject(selector).click()).isTrue()
}

fun clickButtonWithTextMatching(text: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().textMatches(text)
    assertThat(uiDevice.findObject(selector).click()).isTrue()
}

fun setTextForIndex(index: Int, text: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().className(EditText::class.java).instance(index)
    assertThat(uiDevice.findObject(selector).setText(text)).isTrue()
}

fun waitForText(text: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().text(text)
    assertThat(uiDevice.findObject(selector).waitForExists(10_000)).isTrue()
}

fun waitForTextMatching(text: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().textMatches(text)
    assertThat(uiDevice.findObject(selector).waitForExists(10_000)).isTrue()
}

fun waitForResourceIdWithText(resourceId: String, text: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().resourceIdMatches(resourceId).text(text)
    assertThat(uiDevice.findObject(selector).waitForExists(10_000)).isTrue()
}

fun waitForResourceId(resourceId: String) {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = UiSelector().resourceIdMatches(resourceId)
    assertThat(uiDevice.findObject(selector).waitForExists(10_000)).isTrue()
}
