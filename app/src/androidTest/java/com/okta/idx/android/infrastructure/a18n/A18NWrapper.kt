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
package com.okta.idx.android.infrastructure.a18n

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.okta.idx.android.infrastructure.EndToEndCredentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.regex.Pattern

private const val RETRY_COUNT = 5

object A18NWrapper {

    private val client = OkHttpClient()
    private val objectMapper = ObjectMapper()
    init {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun createProfile(): A18NProfile {
        val url = "https://api.a18n.help/v1/profile"
        val request = Builder()
            .url(url)
            .post("""{"displayName":"okta-idx-android"}""".toRequestBody("application/json".toMediaType()))
            .header("x-api-key", EndToEndCredentials["/a18n/token"])
            .build()
        val result: A18NProfile
        client.newCall(request).execute().use { response ->
            result = objectMapper.createParser(response.body?.byteStream())
                .readValueAs(A18NProfile::class.java)
        }
        return result
    }

    fun deleteProfile(profile: A18NProfile) {
        val request = Builder()
                .url(profile.url)
                .delete()
                .header("x-api-key", EndToEndCredentials["/a18n/token"])
                .build()
        client.newCall(request).execute()
    }

    fun getCodeFromEmail(profile: A18NProfile): String {
        val request = Builder()
            .url(profile.url + "/email/latest/content")
            .build()
        var result: String? = null
        var retryCount = RETRY_COUNT
        while (retryCount > 0 && result == null) {
            Thread.sleep(500)
            client.newCall(request).execute().use {
                result = fetchCodeFromRegistrationEmail(it.body!!.string())
            }
            retryCount--
        }
        return result!!
    }
    
    fun getCodeFromPhone(profile: A18NProfile): String {
        val request = Builder()
                .url(profile.url + "/sms/latest/content")
                .build()
        var result: String? = null
        var retryCount = RETRY_COUNT
        while (retryCount > 0 && result == null) {
            Thread.sleep(500)
            client.newCall(request).execute().use {
                val codeSubstring = "code is "
                val body = it.body!!.string()
                val codeStarts = body.indexOf(codeSubstring) + codeSubstring.length
                result = body.substring(codeStarts, codeStarts + 6)
            }
            retryCount--
        }
        return result!!
    }

    private fun fetchCodeFromRegistrationEmail(emailContent: String): String? {
        val pattern = Pattern.compile("To verify manually, enter this code: (\\d{6})")
        val matcher = pattern.matcher(emailContent)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun fetchCodeFromPasswordResetEmail(smsContent: String): String? {
        val pattern = Pattern.compile("Enter a code instead: (\\d{6})")
        val matcher = pattern.matcher(smsContent)
        return if (matcher.find()) matcher.group(1) else null
    }
}