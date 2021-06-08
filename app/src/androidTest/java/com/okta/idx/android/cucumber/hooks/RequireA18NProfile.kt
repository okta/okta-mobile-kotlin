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

import com.okta.idx.android.infrastructure.a18n.A18NWrapper
import io.cucumber.java.After
import io.cucumber.java.Before

class RequireA18NProfile {
    @Before("@requireA18NProfile", order = 0) fun createA18NProfileBeforeScenario() {
        SharedState.a18NProfile = A18NWrapper.createProfile()
    }

    @After("@requireA18NProfile")
    fun removeA18NProfileAfterScenario() {
        SharedState.a18NProfile?.let {
            A18NWrapper.deleteProfile(it)
        }
    }
}
