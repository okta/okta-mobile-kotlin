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
package com.okta.idx.kotlin.dto.v1

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class TokenTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun testDeserialization() {
        val tokenJson = """
        {
          "token_type": "Bearer",
          "expires_in": 3600,
          "access_token": "eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULm01N1NsVUpMRUQyT1RtLXVrUFBEVGxFY0tialFvYy1wVGxVdm5ha0k3T1Eub2FyNjFvOHVVOVlGVnBYcjYybzQiLCJpc3MiOiJodHRwczovL2Zvby5wcmV2aWV3LmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2MDg1NjcwMTgsImV4cCI6MTYwODU3MDYxOCwiY2lkIjoiMG9henNtcHhacFZFZzRjaFMybzQiLCJ1aWQiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsInNjcCI6WyJvcGVuaWQiLCJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiXSwic3ViIjoiZm9vQG9rdGEuY29tIn0.lg2T8dKVfic_JU6qzNBqDuw3RFUq7Da5UO37eY3W-cOOb9UqijxGYj7d-z8qK1UJjRRcDg-rTMzYQbKCLVxjBw",
          "scope": "openid profile offline_access",
          "refresh_token": "CCY4M4fR3",
          "id_token": "eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsIm5hbWUiOiJQYXNzY29kZSBMb2dpbiIsInZlciI6MSwiaXNzIjoiaHR0cHM6Ly9mb28ucHJldmlldy5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiIwb2F6c21weFpwVkVnNGNoUzJvNCIsImlhdCI6MTYwODU2NzAxOCwiZXhwIjoxNjA4NTcwNjE4LCJqdGkiOiJJRC42MS1zTGxqYnBHZGt2Sy03WVpaQ2tYdllXQ0l5X0p0dTVDUHZiWTIzeWJJIiwiYW1yIjpbInB3ZCJdLCJpZHAiOiIwMG96c2VmUENuYU1mNzIwMTJvNCIsInByZWZlcnJlZF91c2VybmFtZSI6ImZvb0Bva3RhLmNvbSIsImF1dGhfdGltZSI6MTYwODU2NzAxNywiYXRfaGFzaCI6IjVjWGpyQmNMVnJqNUQyY2MxN1AwYlEifQ.ICMbF6eabo24617_2XN0YV-49R6MHxmFaRcFl1hryoyxn8HOxJEidTqbjJT5FOYjZVR7twblnuU-iIoW0eUDUg"
        }
        """.trimIndent()
        val token = json.decodeFromString<Token>(tokenJson)
        assertThat(token.tokenType).isEqualTo("Bearer")
        assertThat(token.expiresIn).isEqualTo(3600)
        assertThat(token.accessToken).isEqualTo("eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULm01N1NsVUpMRUQyT1RtLXVrUFBEVGxFY0tialFvYy1wVGxVdm5ha0k3T1Eub2FyNjFvOHVVOVlGVnBYcjYybzQiLCJpc3MiOiJodHRwczovL2Zvby5wcmV2aWV3LmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2MDg1NjcwMTgsImV4cCI6MTYwODU3MDYxOCwiY2lkIjoiMG9henNtcHhacFZFZzRjaFMybzQiLCJ1aWQiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsInNjcCI6WyJvcGVuaWQiLCJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiXSwic3ViIjoiZm9vQG9rdGEuY29tIn0.lg2T8dKVfic_JU6qzNBqDuw3RFUq7Da5UO37eY3W-cOOb9UqijxGYj7d-z8qK1UJjRRcDg-rTMzYQbKCLVxjBw")
        assertThat(token.scope).isEqualTo("openid profile offline_access")
        assertThat(token.refreshToken).isEqualTo("CCY4M4fR3")
        assertThat(token.idToken).isEqualTo("eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsIm5hbWUiOiJQYXNzY29kZSBMb2dpbiIsInZlciI6MSwiaXNzIjoiaHR0cHM6Ly9mb28ucHJldmlldy5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiIwb2F6c21weFpwVkVnNGNoUzJvNCIsImlhdCI6MTYwODU2NzAxOCwiZXhwIjoxNjA4NTcwNjE4LCJqdGkiOiJJRC42MS1zTGxqYnBHZGt2Sy03WVpaQ2tYdllXQ0l5X0p0dTVDUHZiWTIzeWJJIiwiYW1yIjpbInB3ZCJdLCJpZHAiOiIwMG96c2VmUENuYU1mNzIwMTJvNCIsInByZWZlcnJlZF91c2VybmFtZSI6ImZvb0Bva3RhLmNvbSIsImF1dGhfdGltZSI6MTYwODU2NzAxNywiYXRfaGFzaCI6IjVjWGpyQmNMVnJqNUQyY2MxN1AwYlEifQ.ICMbF6eabo24617_2XN0YV-49R6MHxmFaRcFl1hryoyxn8HOxJEidTqbjJT5FOYjZVR7twblnuU-iIoW0eUDUg")
    }

    @Test fun testMiddleware() {
        val tokenJson = """
        {
          "token_type": "Bearer",
          "expires_in": 3600,
          "access_token": "eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULm01N1NsVUpMRUQyT1RtLXVrUFBEVGxFY0tialFvYy1wVGxVdm5ha0k3T1Eub2FyNjFvOHVVOVlGVnBYcjYybzQiLCJpc3MiOiJodHRwczovL2Zvby5wcmV2aWV3LmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2MDg1NjcwMTgsImV4cCI6MTYwODU3MDYxOCwiY2lkIjoiMG9henNtcHhacFZFZzRjaFMybzQiLCJ1aWQiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsInNjcCI6WyJvcGVuaWQiLCJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiXSwic3ViIjoiZm9vQG9rdGEuY29tIn0.lg2T8dKVfic_JU6qzNBqDuw3RFUq7Da5UO37eY3W-cOOb9UqijxGYj7d-z8qK1UJjRRcDg-rTMzYQbKCLVxjBw",
          "scope": "openid profile offline_access",
          "refresh_token": "CCY4M4fR3",
          "id_token": "eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsIm5hbWUiOiJQYXNzY29kZSBMb2dpbiIsInZlciI6MSwiaXNzIjoiaHR0cHM6Ly9mb28ucHJldmlldy5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiIwb2F6c21weFpwVkVnNGNoUzJvNCIsImlhdCI6MTYwODU2NzAxOCwiZXhwIjoxNjA4NTcwNjE4LCJqdGkiOiJJRC42MS1zTGxqYnBHZGt2Sy03WVpaQ2tYdllXQ0l5X0p0dTVDUHZiWTIzeWJJIiwiYW1yIjpbInB3ZCJdLCJpZHAiOiIwMG96c2VmUENuYU1mNzIwMTJvNCIsInByZWZlcnJlZF91c2VybmFtZSI6ImZvb0Bva3RhLmNvbSIsImF1dGhfdGltZSI6MTYwODU2NzAxNywiYXRfaGFzaCI6IjVjWGpyQmNMVnJqNUQyY2MxN1AwYlEifQ.ICMbF6eabo24617_2XN0YV-49R6MHxmFaRcFl1hryoyxn8HOxJEidTqbjJT5FOYjZVR7twblnuU-iIoW0eUDUg"
        }
        """.trimIndent()
        val token = json.decodeFromString<Token>(tokenJson)
        val tokenResponse = token.toIdxResponse()
        assertThat(tokenResponse.tokenType).isEqualTo("Bearer")
        assertThat(tokenResponse.expiresIn).isEqualTo(3600)
        assertThat(tokenResponse.accessToken).isEqualTo("eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULm01N1NsVUpMRUQyT1RtLXVrUFBEVGxFY0tialFvYy1wVGxVdm5ha0k3T1Eub2FyNjFvOHVVOVlGVnBYcjYybzQiLCJpc3MiOiJodHRwczovL2Zvby5wcmV2aWV3LmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2MDg1NjcwMTgsImV4cCI6MTYwODU3MDYxOCwiY2lkIjoiMG9henNtcHhacFZFZzRjaFMybzQiLCJ1aWQiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsInNjcCI6WyJvcGVuaWQiLCJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiXSwic3ViIjoiZm9vQG9rdGEuY29tIn0.lg2T8dKVfic_JU6qzNBqDuw3RFUq7Da5UO37eY3W-cOOb9UqijxGYj7d-z8qK1UJjRRcDg-rTMzYQbKCLVxjBw")
        assertThat(tokenResponse.scope).isEqualTo("openid profile offline_access")
        assertThat(tokenResponse.refreshToken).isEqualTo("CCY4M4fR3")
        assertThat(tokenResponse.idToken).isEqualTo("eyJraWQiOiJBaE1qU3VMQWdBTDJ1dHVVY2lFRWJ2R1JUbi1GRkt1Y2tVTDJibVZMVmp3IiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHUxMGt2dkZDMDZHT21odTJvNSIsIm5hbWUiOiJQYXNzY29kZSBMb2dpbiIsInZlciI6MSwiaXNzIjoiaHR0cHM6Ly9mb28ucHJldmlldy5jb20vb2F1dGgyL2RlZmF1bHQiLCJhdWQiOiIwb2F6c21weFpwVkVnNGNoUzJvNCIsImlhdCI6MTYwODU2NzAxOCwiZXhwIjoxNjA4NTcwNjE4LCJqdGkiOiJJRC42MS1zTGxqYnBHZGt2Sy03WVpaQ2tYdllXQ0l5X0p0dTVDUHZiWTIzeWJJIiwiYW1yIjpbInB3ZCJdLCJpZHAiOiIwMG96c2VmUENuYU1mNzIwMTJvNCIsInByZWZlcnJlZF91c2VybmFtZSI6ImZvb0Bva3RhLmNvbSIsImF1dGhfdGltZSI6MTYwODU2NzAxNywiYXRfaGFzaCI6IjVjWGpyQmNMVnJqNUQyY2MxN1AwYlEifQ.ICMbF6eabo24617_2XN0YV-49R6MHxmFaRcFl1hryoyxn8HOxJEidTqbjJT5FOYjZVR7twblnuU-iIoW0eUDUg")
    }
}
