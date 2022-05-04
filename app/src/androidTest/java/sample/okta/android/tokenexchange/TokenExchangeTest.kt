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
package sample.okta.android.tokenexchange

import androidx.test.rule.ActivityTestRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import sample.okta.android.MainActivity
import sample.okta.android.launch.LaunchPage
import sample.okta.android.test.UserRule
import sample.okta.android.web.WebPage

internal class TokenExchangeTest {
    @get:Rule val activityRule = ActivityTestRule(MainActivity::class.java)
    @get:Rule val userRule = UserRule()

    @Before fun clearWebData() {
        WebPage.clearData()
    }

    @Test fun testTokenExchange() {
        LaunchPage.goToBrowserPage()
            .loginWithWeb()
            .username(userRule.email)
            .password(userRule.password)
            .login()
            .assertIsDefaultCredential()
            .assertHasScope("offline_access email profile openid device_sso")
            .goToTokenExchangeFlow()
            .assertIsTokenExchangeCredential()
            .assertHasScope("offline_access email profile openid")
            .assertMissingScope("device_sso")
    }
}
