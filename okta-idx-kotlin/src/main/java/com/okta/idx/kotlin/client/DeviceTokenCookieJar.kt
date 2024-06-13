/*
 * Copyright 2024-Present Okta, Inc.
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
package com.okta.idx.kotlin.client

import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.client.DeviceTokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import kotlin.time.Duration.Companion.seconds

internal class DeviceTokenCookieJar : CookieJar {
    private val savedCookiesCache = mutableMapOf<String, List<Cookie>>()
    private val oidcClock by lazy { AuthFoundationDefaults.clock }

    private val deviceTokenCookieBuilder: Cookie.Builder
        get() {
            val deviceToken = runBlocking { DeviceTokenProvider.instance.getDeviceToken() }
            return getDtCookieBuilderWith(deviceToken)
        }

    private fun getDtCookieBuilderWith(deviceToken: String): Cookie.Builder {
        return Cookie.Builder()
            .name("DT")
            .value(deviceToken)
            .secure()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val deviceTokenCookie = deviceTokenCookieBuilder.domain(url.host).build()
        val savedCookiesForDomain = savedCookiesCache[url.host]?.filter {
            it.expiresAt > oidcClock.currentTimeEpochSecond().seconds.inWholeMilliseconds
        } ?: emptyList()
        val deviceTokenCookieList = listOf(deviceTokenCookie)
        return savedCookiesForDomain + deviceTokenCookieList
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        savedCookiesCache[url.host] = cookies
    }
}
