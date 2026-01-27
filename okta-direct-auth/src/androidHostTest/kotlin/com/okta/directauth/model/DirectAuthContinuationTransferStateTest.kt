package com.okta.directauth.model

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.api.http.log.LogLevel
import com.okta.directauth.AUTHORIZATION_PENDING_JSON
import com.okta.directauth.TOKEN_RESPONSE_JSON
import com.okta.directauth.contentType
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.KtorHttpExecutor
import com.okta.directauth.http.UNKNOWN_ERROR
import com.okta.directauth.malformedJsonOkMockEngine
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationState.Authenticated
import com.okta.directauth.model.DirectAuthenticationState.AuthorizationPending
import com.okta.directauth.oAuth2ErrorMockEngine
import com.okta.directauth.tokenResponseMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectAuthContinuationTransferStateTest {
    private lateinit var bindingContext: BindingContext

    private fun createDirectAuthenticationContext(apiExecutor: KtorHttpExecutor): DirectAuthenticationContext {
        return DirectAuthenticationContext(
            issuerUrl = "https://example.okta.com",
            clientId = "test_client_id",
            scope = listOf("openid", "email", "profile", "offline_access"),
            authorizationServerId = "",
            clientSecret = "test_client_secret",
            grantTypes = listOf(GrantType.Password, GrantType.Otp),
            acrValues = emptyList(),
            directAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN,
            apiExecutor = apiExecutor,
            logger = object : AuthFoundationLogger {
                override fun write(message: String, tr: Throwable?, logLevel: LogLevel) {
                    // No-op logger for tests
                }
            },
            clock = { 1654041600 }, // 2022-06-01
            additionalParameters = mapOf("custom_param" to "custom_value")
        )
    }

    @Before
    fun setUp() {
        bindingContext = BindingContext(
            oobCode = "test_oob_code",
            expiresIn = 60, // 60 seconds
            interval = 5, // 5 seconds
            channel = OobChannel.PUSH,
            bindingMethod = BindingMethod.TRANSFER,
            bindingCode = "test_binding_code",
            null,
        )
    }

    @Test
    fun `proceed returns Authenticated on first attempt`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
        val transferState = DirectAuthContinuation.Transfer(bindingContext, context)

        val result = runBlocking { transferState.proceed() }

        assertThat(transferState.expirationInSeconds, equalTo(60))
        assertThat(transferState.bindingCode, equalTo("test_binding_code"))
        assertThat(result, instanceOf(Authenticated::class.java))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
        val authenticated = result as Authenticated
        assertThat(authenticated.token.tokenType, equalTo("Bearer"))
        assertThat(authenticated.token.expiresIn, equalTo(3600))
        assertThat(authenticated.token.accessToken, equalTo("example_access_token"))
        assertThat(authenticated.token.scope, equalTo("openid email profile offline_access"))
        assertThat(authenticated.token.refreshToken, equalTo("example_refresh_token"))
        assertThat(authenticated.token.idToken, equalTo("example_id_token"))
    }

    @Test
    fun `proceed returns Authenticated with a mfa context`() {
        val context = createDirectAuthenticationContext(KtorHttpExecutor(HttpClient(tokenResponseMockEngine)))
        val mfaContext = MfaContext(mfaToken = "test_mfa_token", supportedChallengeTypes = listOf(ChallengeGrantType.OobMfa))
        val transferState = DirectAuthContinuation.Transfer(bindingContext, context, mfaContext)

        val result = runBlocking { transferState.proceed() }

        assertThat(result, instanceOf(Authenticated::class.java))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
        val authenticated = result as Authenticated
        assertThat(authenticated.token.tokenType, equalTo("Bearer"))
    }

    @Test
    fun `proceed returns Authenticated after one pending response`() {
        val mockEngine = MockEngine.Queue()
        val collectedValues = mutableListOf<DirectAuthenticationState>()

        mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }
        mockEngine.enqueue { respond(TOKEN_RESPONSE_JSON, HttpStatusCode.OK, contentType) }
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(mockEngine)))
        val transferState = DirectAuthContinuation.Transfer(bindingContext, context)

        val job = CoroutineScope(Dispatchers.Default).launch {
            context.authenticationStateFlow.collect { value ->
                if (value !is DirectAuthenticationState.Idle) collectedValues.add(value)
            }
        }

        val result = runBlocking { transferState.proceed() }

        assertThat(result, instanceOf(Authenticated::class.java))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
        assertThat(mockEngine.responseHistory.first().statusCode, equalTo(HttpStatusCode.BadRequest))
        assertThat(mockEngine.responseHistory.last().statusCode, equalTo(HttpStatusCode.OK))
        assertThat(collectedValues.first(), instanceOf(AuthorizationPending::class.java))
        assertThat(collectedValues.last(), instanceOf(Authenticated::class.java))

        job.cancel()
    }

    @Test
    fun `proceed returns InternalError on timeout`() {
        val mockEngine = MockEngine.Queue()
        mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }
        mockEngine.enqueue { respond(AUTHORIZATION_PENDING_JSON, HttpStatusCode.BadRequest, contentType) }

        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(mockEngine)))
        val transferState = DirectAuthContinuation.Transfer(bindingContext.copy(expiresIn = 1), context)

        val result = runBlocking { transferState.proceed() }

        assertThat(result, instanceOf(InternalError::class.java))
        val error = result as InternalError
        assertThat(error.throwable, instanceOf(TimeoutCancellationException::class.java))
        assertThat(error.description, equalTo("Polling timed out after 1 seconds."))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
    }

    @Test
    fun `proceed returns HttpError on API error`() {
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(oAuth2ErrorMockEngine)))
        val transferState = DirectAuthContinuation.Transfer(bindingContext, context)

        val result = runBlocking { transferState.proceed() }

        assertThat(result, instanceOf(DirectAuthenticationError.HttpError.Oauth2Error::class.java))
        val error = result as DirectAuthenticationError.HttpError.Oauth2Error
        assertThat(error.error, equalTo("invalid_grant"))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
    }

    @Test
    fun `proceed returns InternalError`() {
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(malformedJsonOkMockEngine)))
        val transferState = DirectAuthContinuation.Transfer(bindingContext, context)

        val result = runBlocking { transferState.proceed() }

        assertThat(result, instanceOf(InternalError::class.java))
        val error = result as InternalError
        assertThat(error.errorCode, equalTo(UNKNOWN_ERROR))
        assertThat(error.description, containsString("Unexpected JSON token at offset"))
        assertThat(error.throwable, instanceOf(SerializationException::class.java))
    }

    @Test
    fun `proceed returns InternalError on generic exception`() {
        val mockEngine = MockEngine { throw IOException("Simulated network failure") }
        val context = createDirectAuthenticationContext(apiExecutor = KtorHttpExecutor(HttpClient(mockEngine)))
        val transferState = DirectAuthContinuation.Transfer(bindingContext, context)

        val result = runBlocking { transferState.proceed() }

        assertThat(result, instanceOf(InternalError::class.java))
        val error = result as InternalError
        assertThat(error.errorCode, equalTo(EXCEPTION))
        assertThat(error.description, equalTo("Simulated network failure"))
        assertThat(error.throwable, instanceOf(IOException::class.java))
        assertThat(context.authenticationStateFlow.value, equalTo(result))
    }
}
