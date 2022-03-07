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
package com.okta.authfoundation.client.dto

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.client.dto.OidcIntrospectInfo.Companion.asOidcIntrospectInfo
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test

class OidcIntrospectInfoTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testPayload(): Unit = runBlocking {
        val payload = """{"active":true,"foo":"bar"}"""
        val payloadJson = oktaRule.configuration.json.decodeFromString(JsonObject.serializer(), payload)
        val subject = payloadJson.asOidcIntrospectInfo(oktaRule.configuration)
        val activeSubject = subject as OidcIntrospectInfo.Active
        assertThat(activeSubject.deserializeClaims(ExampleClaim.serializer()).foo).isEqualTo("bar")
    }

    @Test fun testAsOidcIntrospectInfoActive(): Unit = runBlocking {
        val payload = """{"active":true,"foo":"bar"}"""
        val payloadJson = oktaRule.configuration.json.decodeFromString(JsonObject.serializer(), payload)
        val subject = payloadJson.asOidcIntrospectInfo(oktaRule.configuration)
        assertThat(subject).isInstanceOf(OidcIntrospectInfo.Active::class.java)
        assertThat(subject.active).isTrue()
    }

    @Test fun testAsOidcIntrospectInfoInactive(): Unit = runBlocking {
        val payload = """{"active":false}"""
        val payloadJson = oktaRule.configuration.json.decodeFromString(JsonObject.serializer(), payload)
        val subject = payloadJson.asOidcIntrospectInfo(oktaRule.configuration)
        assertThat(subject).isInstanceOf(OidcIntrospectInfo.Inactive::class.java)
        assertThat(subject.active).isFalse()
    }
}

@Serializable
private class ExampleClaim(@SerialName("foo") val foo: String)
