package com.okta.directauth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel

const val TOKEN_RESPONSE_JSON =
    """{"access_token":"example_access_token","token_type":"Bearer","expires_in":3600,"scope":"openid email profile offline_access","refresh_token":"example_refresh_token","id_token":"example_id_token"}"""
const val AUTHORIZATION_PENDING_JSON =
    """{"error":"authorization_pending","error_description":"The user has not yet approved the push notification."}"""
const val OAUTH2_ERROR_JSON =
    """{"error":"invalid_grant","error_description":"The password was invalid."}"""
const val SERVER_ERROR_JSON =
    """{"errorCode":"E00000","errorSummary":"Internal Server Error"}"""
const val MFA_REQUIRED_JSON =
    """{"error":"mfa_required","error_description":"MFA is required for this transaction.","mfa_token":"example_mfa_token"}"""
const val INVALID_MFA_REQUIRED_JSON =
    """{"error":"mfa_required","error_description":"MFA is required for this transaction."}"""
const val UNKNOWN_JSON_TYPE = """{"unknown_json_type":"bad response"}"""

// Malformed JSON with trailing comma
const val MALFORMED_JSON = """{"access_token": "token","token_type": "Bearer",}"""
const val MALFORMED_JSON_ERROR_CODE = """{"errorCode": "error","errorSummary": "error description",}"""
const val MALFORMED_JSON_ERROR = """{"error": "access_denied","error_description": "error description",}"""

fun createMockEngine(
    jsonResponse: String,
    httpStatusCode: HttpStatusCode,
    headers: Headers = headersOf("Content-Type", "application/json")
): MockEngine = MockEngine {
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

val malformedJsonErrorMockEngine = createMockEngine(MALFORMED_JSON_ERROR, HttpStatusCode.BadRequest)

val malformedJsonErrorCodeMockEngine = createMockEngine(MALFORMED_JSON_ERROR_CODE, HttpStatusCode.BadRequest)
