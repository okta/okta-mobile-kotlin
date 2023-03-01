/*
 * Copyright 2023-Present Okta, Inc.
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
package com.okta.authfoundation.client

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class DeviceTokenCookieJar : CookieJar {
    private val savedCookiesCache = mutableMapOf<String, List<Cookie>>()

    private val deviceTokenCookieBuilder = Cookie.Builder()
        .name("DT")
        .value(DeviceTokenProvider.deviceToken)
        .secure()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val deviceTokenCookie = deviceTokenCookieBuilder.domain(url.host).build()
        val savedCookiesForDomain = savedCookiesCache[url.host]?.filter {
            it.expiresAt > System.currentTimeMillis()
        } ?: emptyList()
        return savedCookiesForDomain + listOf(deviceTokenCookie)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        savedCookiesCache[url.host] = cookies
    }
}
