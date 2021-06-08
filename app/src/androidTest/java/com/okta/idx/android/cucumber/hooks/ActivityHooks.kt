/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.android.cucumber.hooks

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.okta.idx.android.MainActivity
import io.cucumber.java.After
import io.cucumber.java.Before

class ActivityHooks {
    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before fun launchActivity() {
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After fun finishActivity() {
        activityScenario.close()
    }
}
