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
package com.okta.idx.android

import com.google.common.truth.Truth.assertThat
import com.okta.idx.android.infrastructure.network.NetworkRule
import com.okta.idx.android.infrastructure.network.testBodyFromFile
import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.network.mock.RequestMatchers.path
import com.okta.idx.sdk.api.client.Clients
import com.okta.idx.sdk.api.client.IDXClient
import com.okta.idx.sdk.api.model.Authenticator
import com.okta.idx.sdk.api.model.Credentials
import com.okta.idx.sdk.api.request.AnswerChallengeRequestBuilder
import com.okta.idx.sdk.api.request.ChallengeRequestBuilder
import com.okta.idx.sdk.api.request.IdentifyRequestBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Arrays
import java.util.Optional

class IdxIntegrationTest {
    @get:Rule val networkRule = NetworkRule()

    private lateinit var idxClient: IDXClient

    @Before fun setup() {
        OktaMockWebServer.dispatcher.consumeResponses = true

        idxClient = Clients.builder()
            .setIssuer(networkRule.mockedUrl())
            .setClientId("test-client-id")
            .setClientSecret("test-client-secret")
            .setScopes(setOf("test-scope-1", "test-scope-2"))
            .setRedirectUri("http://okta.com")
            .build()
    }

    @Test fun testIdx() {
        // interact
        networkRule.enqueue(path("/v1/interact")) { response ->
            response.setBody("""{"interaction_handle": "003Q14X7li"}""")
        }

        val interactResponse = idxClient.interact()

        assertThat(interactResponse).isNotNull()
        assertThat(interactResponse.interactionHandle).isEqualTo("003Q14X7li")

        // introspect
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.testBodyFromFile("introspect-response.json")
        }

        val introspectResponse = idxClient.introspect(Optional.of("interactionHandle"))
        assertThat(introspectResponse).isNotNull()

        // identify
        networkRule.enqueue(path("/idp/idx/identify")) { response ->
            response.testBodyFromFile("identify-response.json")
        }
        val identifyRequest = IdentifyRequestBuilder.builder()
            .withIdentifier("test@example.com")
            .withRememberMe(false)
            .withStateHandle("stateHandle")
            .build()
        val identifyResponse = idxClient.identify(identifyRequest)
        assertThat(identifyResponse).isNotNull()
        assertThat(identifyResponse.remediation()).isNotNull()
        assertThat(identifyResponse.remediation().remediationOptions()).isNotNull()

        // get remediation options to go to the next step
        val remediationOptions = identifyResponse.remediation().remediationOptions()
        val remediationOptionsOptional = Arrays.stream(remediationOptions)
            .filter { x -> ("select-authenticator-authenticate" == x.name) }
            .findFirst()
        val remediationOption = remediationOptionsOptional.get()

        // get authenticator options
        val authenticatorOptionsMap = remediationOption.authenticatorOptions

        assertThat(authenticatorOptionsMap).hasSize(3)
        assertThat(authenticatorOptionsMap).containsEntry("password", "aut2ihzk2n15tsQnQ1d6")
        assertThat(authenticatorOptionsMap).containsEntry(
            "security_question",
            "aut2ihzk4hgf9sIQa1d6"
        )
        assertThat(authenticatorOptionsMap).containsEntry("email", "aut2ihzk1gHl7ynhd1d6")

        // select password authenticator challenge
        networkRule.enqueue(path("/idp/idx/challenge")) { response ->
            response.testBodyFromFile("password-authenticator-challenge-response.json")
        }

        val passwordAuthenticator = Authenticator()
        passwordAuthenticator.id = authenticatorOptionsMap["password"]
        passwordAuthenticator.methodType = "password"

        val passwordAuthenticatorChallengeRequest = ChallengeRequestBuilder.builder()
            .withStateHandle("stateHandle")
            .withAuthenticator(passwordAuthenticator)
            .build()
        val selectPasswordResponse =
            remediationOption.proceed(idxClient, passwordAuthenticatorChallengeRequest)

        assertThat(selectPasswordResponse).isNotNull()
        assertThat(selectPasswordResponse.remediation()).isNotNull()
        assertThat(selectPasswordResponse.remediation().remediationOptions()).isNotNull()

        // answer password authenticator challenge
        val answerPasswordRemediationOptions =
            selectPasswordResponse.remediation().remediationOptions()
        val answerPasswordRemediationOptionsOptional =
            Arrays.stream(answerPasswordRemediationOptions)
                .filter { x -> ("challenge-authenticator" == x.name) }
                .findFirst()
        val answerPasswordRemediationOption = answerPasswordRemediationOptionsOptional.get()

        networkRule.enqueue(path("/idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("answer-password-authenticator-challenge-response.json")
        }

        val passwordCredentials = Credentials()
        passwordCredentials.setPasscode("some=password".toCharArray())

        val passwordAuthenticatorAnswerChallengeRequest = AnswerChallengeRequestBuilder.builder()
            .withStateHandle("stateHandle")
            .withCredentials(passwordCredentials)
            .build()
        val passwordResponse = answerPasswordRemediationOption.proceed(
            idxClient,
            passwordAuthenticatorAnswerChallengeRequest
        )

        assertThat(passwordResponse).isNotNull()
        assertThat(passwordResponse.remediation()).isNotNull()
        assertThat(passwordResponse.remediation().remediationOptions()).isNotNull()

        // get remediation options to go to the next step
        val passwordRemediationOptions = passwordResponse.remediation().remediationOptions()
        val passwordRemediationOptionsOptional = Arrays.stream(passwordRemediationOptions)
            .filter { x -> ("select-authenticator-authenticate" == x.name) }
            .findFirst()
        val passwordRemediationOption = passwordRemediationOptionsOptional.get()

        val passwordAuthenticatorOptionsMap = passwordRemediationOption.authenticatorOptions

        assertThat(passwordAuthenticatorOptionsMap).hasSize(1)
        assertThat(passwordAuthenticatorOptionsMap).containsEntry("email", "aut2ihzk1gHl7ynhd1d6")

        // select email authenticator challenge (only one remaining)
        networkRule.enqueue(path("/idp/idx/challenge")) { response ->
            response.testBodyFromFile("email-authenticator-challenge-response.json")
        }

        val emailAuthenticator = Authenticator()
        emailAuthenticator.id = authenticatorOptionsMap["email"]
        emailAuthenticator.methodType = "email"

        val emailAuthenticatorChallengeRequest = ChallengeRequestBuilder.builder()
            .withStateHandle("stateHandle")
            .withAuthenticator(emailAuthenticator)
            .build()
        val emailResponse = remediationOption.proceed(idxClient, emailAuthenticatorChallengeRequest)

        assertThat(emailResponse).isNotNull()
        assertThat(emailResponse.remediation()).isNotNull()
        assertThat(emailResponse.remediation().remediationOptions()).isNotNull()

        // answer email authenticator challenge
        val emailRemediationOptions = emailResponse.remediation().remediationOptions()
        val emailRemediationOptionsOptional = Arrays.stream(emailRemediationOptions)
            .filter { x -> ("challenge-authenticator" == x.name) }
            .findFirst()
        val emailRemediationOption = emailRemediationOptionsOptional.get()

        networkRule.enqueue(path("/idp/idx/challenge/answer")) { response ->
            response.testBodyFromFile("answer-email-authenticator-challenge-response.json")
        }

        val emailPasscodeCredentials = Credentials()
        emailPasscodeCredentials.setPasscode("some-email-passcode".toCharArray())

        val emailAuthenticatorAnswerChallengeRequest = AnswerChallengeRequestBuilder.builder()
            .withStateHandle("stateHandle")
            .withCredentials(emailPasscodeCredentials)
            .build()
        val emailAnswerResponse =
            emailRemediationOption.proceed(idxClient, emailAuthenticatorAnswerChallengeRequest)

        assertThat(emailAnswerResponse).isNotNull()
        assertThat(emailAnswerResponse.remediation()).isNull() // no more remediation steps

        assertThat(emailAnswerResponse.successWithInteractionCode).isNotNull()
        assertThat(emailAnswerResponse.successWithInteractionCode.rel).isNotNull()
        assertThat(emailAnswerResponse.successWithInteractionCode.name).isNotNull()
        assertThat(emailAnswerResponse.successWithInteractionCode.href).isNotNull()
        assertThat(emailAnswerResponse.successWithInteractionCode.method).isEqualTo("POST")
        assertThat(emailAnswerResponse.successWithInteractionCode.value).isNotNull()
    }
}
