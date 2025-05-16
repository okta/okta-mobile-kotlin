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
package sample.okta.android.resourceowner

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import sample.okta.android.MainActivity
import sample.okta.android.launch.LaunchPage
import sample.okta.android.test.UserRule

@RunWith(AndroidJUnit4::class)
internal class ResourceOwnerTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule val userRule = UserRule()

    @Test fun testResourceOwner() {
        LaunchPage
            .goToResourceOwnerPage()
            .username(userRule.email)
            .password(userRule.password)
            .login()
            .assertIsDefaultCredential()
            .assertHasClaim("email", userRule.email)
    }

    @Test fun testResourceOwnerWithError() {
        LaunchPage
            .goToResourceOwnerPage()
            .username(userRule.email)
            .password("Invalid")
            .loginExpectingError()
            .assertHasError("An error occurred.")
    }
}
