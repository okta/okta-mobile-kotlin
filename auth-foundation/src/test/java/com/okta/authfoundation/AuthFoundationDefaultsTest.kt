/*
 * Copyright 2025-Present Okta, Inc.
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
package com.okta.authfoundation

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import okhttp3.CookieJar
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import kotlin.test.Test

class AuthFoundationDefaultsTest {
    private lateinit var defaultSdkState: DefaultSdkState

    @MockK
    private lateinit var sdkDefaultCookieJar: CookieJar

    private lateinit var cookieJarDelegate: NoSetAfterGetWithLazyDefaultFactory<CookieJar>

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        defaultSdkState = DefaultSdkState(SdkDefaults.getCookieJar)
        SdkDefaults.getCookieJar = { sdkDefaultCookieJar }

        cookieJarDelegate = NoSetAfterGetWithLazyDefaultFactory { SdkDefaults.getCookieJar() }
    }

    @After
    fun tearDown() {
        unmockkAll()
        with(defaultSdkState) {
            SdkDefaults.getCookieJar = getCookieJar
        }
    }

    @Test
    fun `get default cookie jar`() {
        val cookieJar by cookieJarDelegate
        assertThat(cookieJar, `is`(sdkDefaultCookieJar))
    }

    @Test
    fun `set different sdkDefinedCookieJar, expect new value`() {
        val oldCookieJar = sdkDefaultCookieJar
        val newCookieJar = CookieJar.NO_COOKIES

        sdkDefaultCookieJar = newCookieJar
        val cookieJar by cookieJarDelegate
        assertThat(cookieJar, `is`(not(oldCookieJar)))
        assertThat(cookieJar, `is`(newCookieJar))
    }

    @Test
    fun `set different sdkDefinedCookieJar after accessing it, expect old value`() {
        val oldCookieJar = sdkDefaultCookieJar
        val newCookieJar = CookieJar.NO_COOKIES

        val cookieJar by cookieJarDelegate
        assertThat(cookieJar, `is`(oldCookieJar))
        assertThat(cookieJar, `is`(not(newCookieJar)))

        sdkDefaultCookieJar = newCookieJar

        val cookieJar2 by cookieJarDelegate
        assertThat(cookieJar2, `is`(oldCookieJar))
        assertThat(cookieJar2, `is`(not(newCookieJar)))
    }

    private class DefaultSdkState(val getCookieJar: () -> CookieJar)
}
