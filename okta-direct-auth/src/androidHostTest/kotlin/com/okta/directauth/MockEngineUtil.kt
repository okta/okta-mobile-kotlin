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
package com.okta.directauth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

const val TOKEN_RESPONSE_JSON =
    """{"access_token":"example_access_token","token_type":"Bearer","expires_in":3600,"scope":"openid email profile offline_access","refresh_token":"example_refresh_token","id_token":"example_id_token"}"""
const val AUTHORIZATION_PENDING_JSON = """{"error":"authorization_pending","error_description":"The user has not yet approved the push notification."}"""
const val OAUTH2_ERROR_JSON = """{"error":"invalid_grant","error_description":"The password was invalid."}"""
const val SERVER_ERROR_JSON = """{"errorCode":"E00000","errorSummary":"Internal Server Error"}"""
const val MFA_REQUIRED_JSON = """{"error":"mfa_required","error_description":"MFA is required for this transaction.","mfa_token":"example_mfa_token"}"""
const val INVALID_MFA_REQUIRED_JSON = """{"error":"mfa_required","error_description":"MFA is required for this transaction."}"""
const val UNKNOWN_JSON_TYPE = """{"unknown_json_type":"bad response"}"""
const val OOB_AUTHENTICATE_PUSH_RESPONSE_JSON =
    """{"challenge_type":"http://auth0.com/oauth/grant-type/mfa-oob","oob_code":"example_oob_code","channel":"push","binding_method":"none","expires_in":120,"interval":5}"""
const val OOB_AUTHENTICATE_SMS_RESPONSE_JSON = """{"oob_code":"example_oob_code","channel":"sms","binding_method":"prompt","expires_in":120}"""
const val OOB_AUTHENTICATE_VOICE_RESPONSE_JSON = """{"oob_code":"example_oob_code","channel":"voice","binding_method":"prompt","expires_in":120}"""
const val OOB_AUTHENTICATE_TRANSFER_RESPONSE_JSON =
    """{"challenge_type":"http://auth0.com/oauth/grant-type/mfa-oob","oob_code":"example_oob_code","channel":"push","binding_method":"transfer","binding_code":"95","expires_in":120,"interval":5}"""
const val OOB_AUTHENTICATE_TRANSFER_NO_BINDING_CODE_RESPONSE_JSON = """{"oob_code":"example_oob_code","channel":"push","binding_method":"transfer","expires_in":120,"interval":5}"""
const val CHALLENGE_OTP_RESPONSE_JSON = """{"challenge_type":"http://auth0.com/oauth/grant-type/mfa-otp"}"""
const val CHALLENGE_WEBAUTHN_RESPONSE_JSON = """{"challengeType":"urn:okta:params:oauth:grant-type:webauthn","publicKey":{}} """

// email is not a unsupported channel
const val OOB_AUTHENTICATE_EMAIL_RESPONSE_JSON = """{"oob_code":"example_oob_code","channel":"email","binding_method":"prompt","expires_in":120}"""
const val OOB_AUTHENTICATE_INVALID_BINDING_RESPONSE_JSON = """{"oob_code":"example_oob_code","channel":"push","binding_method":"bluetooth","expires_in":120}"""

const val OOB_AUTHENTICATE_OAUTH2_ERROR_JSON = """{"error":"invalid_request","error_description":"abc is not a valid channel hint"}"""

// Malformed JSON with trailing comma
const val MALFORMED_JSON = """{"access_token": "token","token_type": "Bearer",}"""
const val MALFORMED_JSON_ERROR_CODE = """{"errorCode": "error","errorSummary": "error description",}"""
const val MALFORMED_JSON_ERROR = """{"error": "access_denied","error_description": "error description",}"""

val contentType = headersOf("Content-Type", "application/json")

fun createMockEngine(
    jsonResponse: String,
    httpStatusCode: HttpStatusCode,
    headers: Headers = contentType,
): MockEngine =
    MockEngine {
        respond(
            content = ByteReadChannel(jsonResponse),
            status = httpStatusCode,
            headers = headers
        )
    }

val tokenResponseMockEngine = createMockEngine(TOKEN_RESPONSE_JSON, HttpStatusCode.OK)

val authorizationPendingMockEngine = createMockEngine(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest)

val oAuth2ErrorMockEngine = createMockEngine(OAUTH2_ERROR_JSON, HttpStatusCode.BadRequest)

val serverErrorMockEngine = createMockEngine(SERVER_ERROR_JSON, HttpStatusCode.InternalServerError)

val mfaRequiredMockEngine = createMockEngine(MFA_REQUIRED_JSON, HttpStatusCode.BadRequest)

val invalidMfaRequiredMockEngine = createMockEngine(INVALID_MFA_REQUIRED_JSON, HttpStatusCode.BadRequest)

val notJsonMockEngine = createMockEngine("This is not JSON", HttpStatusCode.BadRequest, headersOf("Content-Type", "text/plain"))

val unexpectedStatusCodeMockEngine = createMockEngine("", HttpStatusCode.Found)

val unknownJsonTypeMockEngine = createMockEngine(UNKNOWN_JSON_TYPE, HttpStatusCode.BadRequest)

val internalServerErrorMockEngine = createMockEngine("", HttpStatusCode.InternalServerError)

val emptyResponseMockEngine = createMockEngine("", HttpStatusCode.BadRequest)

val emptyResponseOkMockEngine = createMockEngine("", HttpStatusCode.OK)

val malformedJsonOkMockEngine = createMockEngine(MALFORMED_JSON, HttpStatusCode.OK)

val malformedJsonClientMockEngine = createMockEngine(MALFORMED_JSON_ERROR_CODE, HttpStatusCode.TooManyRequests)

val malformedJsonErrorMockEngine = createMockEngine(MALFORMED_JSON_ERROR, HttpStatusCode.BadRequest)

val malformedJsonErrorCodeMockEngine = createMockEngine(MALFORMED_JSON_ERROR_CODE, HttpStatusCode.BadRequest)

val oobAuthenticatePushResponseMockEngine = createMockEngine(OOB_AUTHENTICATE_PUSH_RESPONSE_JSON, HttpStatusCode.OK)

val oobAuthenticateSmsResponseMockEngine = createMockEngine(OOB_AUTHENTICATE_SMS_RESPONSE_JSON, HttpStatusCode.OK)

val oobAuthenticateVoiceResponseMockEngine = createMockEngine(OOB_AUTHENTICATE_VOICE_RESPONSE_JSON, HttpStatusCode.OK)

val oobAuthenticateTransferResponseMockEngine = createMockEngine(OOB_AUTHENTICATE_TRANSFER_RESPONSE_JSON, HttpStatusCode.OK)

val oobAuthenticateTransferNoBindingCodeResponseMockEngine = createMockEngine(OOB_AUTHENTICATE_TRANSFER_NO_BINDING_CODE_RESPONSE_JSON, HttpStatusCode.OK)

val oobAuthenticateEmailResponseMockEngine = createMockEngine(OOB_AUTHENTICATE_EMAIL_RESPONSE_JSON, HttpStatusCode.OK)

val oobAuthenticateInvalidBindingResponseMockEngine = createMockEngine(OOB_AUTHENTICATE_INVALID_BINDING_RESPONSE_JSON, HttpStatusCode.OK)

val oobAuthenticateOauth2ErrorMockEngine = createMockEngine(OOB_AUTHENTICATE_OAUTH2_ERROR_JSON, HttpStatusCode.BadRequest)

val challengeOtpResponseMockEngine = createMockEngine(CHALLENGE_OTP_RESPONSE_JSON, HttpStatusCode.OK)

val challengeWebAuthnResponseMockEngine = createMockEngine(CHALLENGE_WEBAUTHN_RESPONSE_JSON, HttpStatusCode.OK)
