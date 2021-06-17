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

import android.app.Activity
import com.okta.idx.android.infrastructure.a18n.A18NProfile
import com.okta.sdk.resource.user.User
import io.cucumber.java.After

class SharedState {
    companion object {
        var user: User? = null
        var a18NProfile: A18NProfile? = null
        var activity: Activity? = null
    }

    @After(order = Int.MIN_VALUE) fun clearSharedState() {
        user = null
        a18NProfile = null
    }
}
