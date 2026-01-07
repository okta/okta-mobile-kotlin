package com.okta.directauth.http

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.api.http.log.LogLevel
import com.okta.directauth.authorizationPendingMockEngine
import com.okta.directauth.emptyResponseMockEngine
import com.okta.directauth.emptyResponseOkMockEngine
import com.okta.directauth.internalServerErrorMockEngine
import com.okta.directauth.invalidMfaRequiredMockEngine
import com.okta.directauth.malformedJsonClientMockEngine
import com.okta.directauth.malformedJsonErrorCodeMockEngine
import com.okta.directauth.malformedJsonErrorMockEngine
import com.okta.directauth.malformedJsonOkMockEngine
import com.okta.directauth.mfaRequiredMockEngine
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.HttpError
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.MfaContext
import com.okta.directauth.notJsonMockEngine
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.serverErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import com.okta.directauth.unexpectedStatusCodeMockEngine
import com.okta.directauth.unknownJsonTypeMockEngine
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.jvm.java

class DirectAuthTokenRequestTest {
    private lateinit var context: DirectAuthenticationContext

    private val supportedGrantTypes = listOf(GrantType.Password, GrantType.Oob, GrantType.Otp, ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa, GrantType.WebAuthn, ChallengeGrantType.WebAuthnMfa)

    @Before
    fun setUp() {
        context = DirectAuthenticationContext(
            issuerUrl = "https://example.okta.com",
            clientId = "test_client_id",
            scope = listOf("openid", "email", "profile", "offline_access"),
            authorizationServerId = "",
            clientSecret = "test_client_secret",
            grantTypes = listOf(GrantType.Password, GrantType.Otp),
            acrValues = emptyList(),
            directAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN,
            apiExecutor = KtorHttpExecutor(),
            logger = object : AuthFoundationLogger {
                override fun write(message: String, tr: Throwable?, logLevel: LogLevel) {
                    // No-op logger for tests
                }
            },
            clock = { 1654041600 }, // 2022-06-01
            additionalParameters = mapOf("custom_param" to "custom_value")
        )
    }

    private fun assertCommonProperties(request: DirectAuthTokenRequest) {
        assertThat(request.url(), equalTo("https://example.okta.com/oauth2/v1/token"))
        assertThat(request.method(), equalTo(ApiRequestMethod.POST))
        assertThat(request.contentType(), equalTo("application/x-www-form-urlencoded"))
        assertThat(request.headers(), equalTo(mapOf("Accept" to listOf("application/json"))))
        assertThat(request.query(), equalTo(mapOf("custom_param" to "custom_value")))
    }

    @Test
    fun passwordRequest_buildsRequestWithCorrectParameters() {
        val request = DirectAuthTokenRequest.Password(
            context = context,
            username = "test_user",
            password = "test_password"
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["client_id"], equalTo(listOf("test_client_id")))
        assertThat(formParameters["client_secret"], equalTo(listOf("test_client_secret")))
        assertThat(formParameters["scope"], equalTo(listOf("openid email profile offline_access")))
        assertThat(formParameters["grant_types_supported"], equalTo(listOf("${GrantType.Password.value} ${GrantType.Otp.value}")))
        assertThat(formParameters["grant_type"], equalTo(listOf(GrantType.Password.value)))
        assertThat(formParameters["username"], equalTo(listOf("test_user")))
        assertThat(formParameters["password"], equalTo(listOf("test_password")))
    }

    @Test
    fun passwordRequest_buildsRequestWithRecoveryIntent() {
        val recoveryContext = context.copy(directAuthenticationIntent = DirectAuthenticationIntent.RECOVERY, scope = listOf("okta.myAccount.password.manage"))
        val request = DirectAuthTokenRequest.Password(
            context = recoveryContext,
            username = "test_user",
            password = "test_password"
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["client_id"], equalTo(listOf("test_client_id")))
        assertThat(formParameters["client_secret"], equalTo(listOf("test_client_secret")))
        assertThat(formParameters["scope"], equalTo(listOf("okta.myAccount.password.manage")))
        assertThat(formParameters["prompt"], equalTo(listOf("recover_authenticator")))
        assertThat(formParameters["grant_types_supported"], equalTo(listOf("${GrantType.Password.value} ${GrantType.Otp.value}")))
        assertThat(formParameters["grant_type"], equalTo(listOf(GrantType.Password.value)))
        assertThat(formParameters["username"], equalTo(listOf("test_user")))
        assertThat(formParameters["password"], equalTo(listOf("test_password")))
    }

    @Test
    fun otpRequest_buildsRequestWithCorrectParameters() {
        val request = DirectAuthTokenRequest.Otp(
            context = context,
            loginHint = "test_user",
            otp = "123456"
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["grant_type"], equalTo(listOf(GrantType.Otp.value)))
        assertThat(formParameters["login_hint"], equalTo(listOf("test_user")))
        assertThat(formParameters["otp"], equalTo(listOf("123456")))
    }

    @Test
    fun mfaOtpRequest_buildsRequestWithCorrectParameters() {
        val mfaContext = MfaContext(supportedGrantTypes, "test_mfa_token")
        val request = DirectAuthTokenRequest.MfaOtp(
            context = context,
            otp = "123456",
            mfaContext = mfaContext,
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["grant_type"], equalTo(listOf(ChallengeGrantType.OtpMfa.value)))
        assertThat(formParameters["mfa_token"], equalTo(listOf("test_mfa_token")))
        assertThat(formParameters["otp"], equalTo(listOf("123456")))
    }

    @Test
    fun oobRequest_buildsRequestWithCorrectParameters() {
        val request = DirectAuthTokenRequest.Oob(
            context = context,
            oobCode = "test_oob_code",
            bindingCode = "95"
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["grant_type"], equalTo(listOf(GrantType.Oob.value)))
        assertThat(formParameters["oob_code"], equalTo(listOf("test_oob_code")))
        assertThat(formParameters["binding_code"], equalTo(listOf("95")))
    }

    @Test
    fun oobMfaRequest_buildsRequestWithCorrectParameters() {
        val mfaContext = MfaContext(supportedGrantTypes, "test_mfa_token")
        val request = DirectAuthTokenRequest.OobMfa(
            context = context,
            oobCode = "test_oob_code",
            mfaContext = mfaContext,
            bindingCode = "test_binding_code"
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["grant_type"], equalTo(listOf(ChallengeGrantType.OobMfa.value)))
        assertThat(formParameters["mfa_token"], equalTo(listOf("test_mfa_token")))
        assertThat(formParameters["oob_code"], equalTo(listOf("test_oob_code")))
        assertThat(formParameters["binding_code"], equalTo(listOf("test_binding_code")))
    }

    @Test
    fun webAuthnRequest_buildsRequestWithCorrectParameters() {
        val request = DirectAuthTokenRequest.WebAuthn(
            context = context,
            authenticatorData = "test_authenticator_data",
            clientDataJson = "test_client_data_json",
            signature = "test_signature"
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["grant_type"], equalTo(listOf(GrantType.WebAuthn.value)))
        assertThat(formParameters["authenticatorData"], equalTo(listOf("test_authenticator_data")))
        assertThat(formParameters["clientDataJSON"], equalTo(listOf("test_client_data_json")))
        assertThat(formParameters["signature"], equalTo(listOf("test_signature")))
    }

    @Test
    fun webAuthnMfaRequest_buildsRequestWithCorrectParameters() {
        val mfaContext = MfaContext(supportedGrantTypes, "test_mfa_token")
        val request = DirectAuthTokenRequest.WebAuthnMfa(
            context = context,
            mfaContext = mfaContext,
            authenticatorData = "test_authenticator_data",
            clientDataJson = "test_client_data_json",
            signature = "test_signature"
        )

        assertCommonProperties(request)

        val formParameters = request.formParameters()
        assertThat(formParameters["grant_type"], equalTo(listOf(ChallengeGrantType.WebAuthnMfa.value)))
        assertThat(formParameters["mfa_token"], equalTo(listOf("test_mfa_token")))
        assertThat(formParameters["authenticatorData"], equalTo(listOf("test_authenticator_data")))
        assertThat(formParameters["clientDataJSON"], equalTo(listOf("test_client_data_json")))
        assertThat(formParameters["signature"], equalTo(listOf("test_signature")))
    }

    @Test
    fun request_usesAuthorizationServerIdInUrlPath() {
        val customAuthServerContext = DirectAuthenticationContext(
            issuerUrl = "https://example.okta.com",
            clientId = "test_client_id",
            scope = listOf("openid", "email", "profile", "offline_access"),
            authorizationServerId = "default", // Custom Authorization Server ID
            clientSecret = "test_client_secret",
            grantTypes = listOf(GrantType.Password, GrantType.Otp),
            acrValues = emptyList(),
            directAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN,
            apiExecutor = KtorHttpExecutor(),
            logger = context.logger,
            clock = { 1654041600 },
            additionalParameters = emptyMap()
        )

        val request = DirectAuthTokenRequest.Password(
            context = customAuthServerContext,
            username = "test_user",
            password = "test_password"
        )

        assertThat(request.url(), equalTo("https://example.okta.com/oauth2/default/v1/token"))
    }

    @Test
    fun request_parsesTokenResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(tokenResponseMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(request.url(), equalTo("https://example.okta.com/oauth2/v1/token"))
        assertThat(directAuthState, instanceOf(DirectAuthenticationState.Authenticated::class.java))
        val token = (directAuthState as DirectAuthenticationState.Authenticated).token
        assertThat(token.accessToken, equalTo("example_access_token"))
    }

    @Test
    fun request_parsesOAuth2ErrorResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(HttpError.Oauth2Error::class.java))
        val apiError = (directAuthState as HttpError.Oauth2Error)
        assertThat(apiError.error, equalTo("invalid_grant"))
        assertThat(apiError.errorDescription, equalTo("The password was invalid."))
        assertThat(apiError.httpStatusCode, equalTo(HttpStatusCode.BadRequest))
    }

    @Test
    fun request_handlesServerError() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(serverErrorMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(HttpError.ApiError::class.java))
        val apiError = (directAuthState as HttpError.ApiError)
        assertThat(apiError.errorCode, equalTo("E00000"))
        assertThat(apiError.errorSummary, equalTo("Internal Server Error"))
        assertThat(apiError.httpStatusCode, equalTo(HttpStatusCode.InternalServerError))
    }

    @Test
    fun request_parsesMfaRequiredResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(mfaRequiredMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(DirectAuthenticationState.MfaRequired::class.java))
        val mfaRequired = directAuthState as DirectAuthenticationState.MfaRequired
        assertThat(mfaRequired.mfaContext.mfaToken, equalTo("example_mfa_token"))
    }

    @Test
    fun request_parsesMfaRequiredResponseWithoutMfaToken() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(invalidMfaRequiredMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)


        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(INVALID_RESPONSE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("No mfa_token found in body: HTTP ${HttpStatusCode.BadRequest}"))
    }

    @Test
    fun request_parsesAuthorizationPendingResponse() = runTest {

        val request = DirectAuthTokenRequest.Oob(context, "test_oob_code")

        val apiResponse = KtorHttpExecutor(HttpClient(authorizationPendingMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(DirectAuthenticationState.AuthorizationPending::class.java))
    }

    @Test
    fun request_handlesUnsupportedContentType() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(notJsonMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(UNSUPPORTED_CONTENT_TYPE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("Unsupported content type: text/plain"))
    }

    @Test
    fun request_handlesUnexpectedStatusCode() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(unexpectedStatusCodeMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(UNEXPECTED_HTTP_STATUS))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("Unexpected HTTP Status Code: ${HttpStatusCode.Found}"))
    }

    @Test
    fun request_unparseableClientErrorResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(unknownJsonTypeMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(INVALID_RESPONSE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("No parsable error response body: HTTP ${HttpStatusCode.BadRequest}"))
    }

    @Test
    fun request_unparseableServerErrorResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")

        val apiResponse = KtorHttpExecutor(HttpClient(internalServerErrorMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(INVALID_RESPONSE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("No parsable error response body: HTTP ${HttpStatusCode.InternalServerError}"))
    }

    @Test
    fun request_emptyErrorResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
        val apiResponse = KtorHttpExecutor(HttpClient(emptyResponseMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(INVALID_RESPONSE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("No parsable error response body: HTTP ${HttpStatusCode.BadRequest}"))
    }

    @Test
    fun request_emptySuccessResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
        val apiResponse = KtorHttpExecutor(HttpClient(emptyResponseOkMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(INVALID_RESPONSE))
        assertThat(error.throwable, instanceOf(IllegalStateException::class.java))
        assertThat(error.throwable.message, equalTo("Empty response body: HTTP ${HttpStatusCode.OK}"))
    }

    @Test
    fun request_malformedJsonInHttpOkStatus() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
        val apiResponse = KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(UNKNOWN_ERROR))
        assertThat(error.description, containsString("Unexpected JSON token at offset"))
        assertThat(error.throwable, instanceOf(SerializationException::class.java))
    }

    @Test
    fun request_malformedJsonInClientErrorStatus() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
        val apiResponse = KtorHttpExecutor(HttpClient(malformedJsonClientMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(UNKNOWN_ERROR))
        assertThat(error.description, containsString("Unexpected JSON token at offset"))
        assertThat(error.throwable, instanceOf(SerializationException::class.java))
    }

    @Test
    fun request_malformedJsonInDirectAuthenticationErrorResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
        val apiResponse = KtorHttpExecutor(HttpClient(malformedJsonErrorMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(UNKNOWN_ERROR))
        assertThat(error.description, containsString("Unexpected JSON token at offset"))
        assertThat(error.throwable, instanceOf(SerializationException::class.java))
    }

    @Test
    fun request_malFormedJsonInErrorResponse() = runTest {
        val request = DirectAuthTokenRequest.Password(context, "test_user", "test_password")
        val apiResponse = KtorHttpExecutor(HttpClient(malformedJsonErrorCodeMockEngine)).execute(request).getOrThrow()
        val directAuthState = apiResponse.tokenResponseAsState(context)

        assertThat(directAuthState, instanceOf(InternalError::class.java))
        val error = directAuthState as InternalError
        assertThat(error.errorCode, equalTo(UNKNOWN_ERROR))
        assertThat(error.description, containsString("Unexpected JSON token at offset"))
        assertThat(error.throwable, instanceOf(SerializationException::class.java))
    }
}
