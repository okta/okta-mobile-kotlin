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
package com.okta.webauthenticationui

import android.app.Activity
import android.content.Context
import okhttp3.HttpUrl

/**
 * Used to launch the OIDC redirect flow associated with a [WebAuthentication].
 */
interface WebAuthenticationProvider {
    /**
     * Launches the OIDC redirect flow associated with a [WebAuthentication].
     *
     * @param context the Android [Activity] [Context] which is used to display the flow.
     * @param url the url the instance should display.
     *
     * @return the exception causing the launch to fail.
     */
    fun launch(
        context: Context,
        url: HttpUrl,
    ): Exception?
}
