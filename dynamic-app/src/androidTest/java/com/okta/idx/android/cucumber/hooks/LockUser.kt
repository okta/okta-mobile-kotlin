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

import com.okta.idx.android.dynamic.BuildConfig
import io.cucumber.java.Before
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class LockUser {
    @Before("@lockUser") fun lockUser() {
        val client = OkHttpClient()
        val url = BuildConfig.ISSUER.toHttpUrl().newBuilder().addPathSegments("v1/token").build()
        val formBody = FormBody.Builder()
            .add("client_id", BuildConfig.CLIENT_ID)
            .add("scope", "openid email profile offline_access")
            .add("grant_type", "password")
            .add("username", SharedState.a18NProfile!!.emailAddress)
            .add("password", "wrong for locking user out.")
            .build()
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        repeat(3) { client.newCall(request).execute() }
    }
}
