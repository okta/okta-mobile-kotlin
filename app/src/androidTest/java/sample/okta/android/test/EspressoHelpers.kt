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

import android.view.View
import android.widget.TextView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import java.util.concurrent.atomic.AtomicReference

fun ViewInteraction.getText(): String {
    val text = AtomicReference<String>()
    perform(object : ViewAction {
        override fun getConstraints() = isAssignableFrom(TextView::class.java)

        override fun getDescription() = "Get text from View: ${text.get()}"

        override fun perform(uiController: UiController, view: View) {
            val tv = view as TextView
            text.set(tv.text.toString())
        }
    })
    return text.get()
}
