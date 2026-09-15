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
package com.okta.directauth.app

import com.okta.directauth.app.model.SubjectKind
import com.okta.directauth.app.util.CrossAppAccessErrors
import com.okta.directauth.app.util.FailureAttribution
import com.okta.oauth2.kmp.CrossAppAccessFlow
import com.okta.oauth2.kmp.CrossAppAccessTarget
import com.okta.oauth2.kmp.SubjectAssertion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies [CrossAppAccessErrors.classify] attributes every failure to the server that produced
 * it, rather than to a generic transport error.
 *
 * Every [com.okta.oauth2.kmp.CrossAppAccessException] used here is genuine, produced by driving a
 * real [CrossAppAccessFlow] against a fake executor: the exception's constructors are internal to
 * the `oauth2` module, so a hand-built double could not stand in for it here.
 */
class CrossAppAccessErrorsTest {
    private val idpIssuer = "https://idp.example.com"
    private val targetIssuer = "https://resource.example.com"

    private fun buildFlow(executor: RecordingApiExecutor): CrossAppAccessFlow {
        val idpClient = buildTestOAuth2Client(idpIssuer, "idp-client-id", executor)
        val targetClient =
            buildTestOAuth2Client(targetIssuer, "idp-client-id", executor, clientSecret = "target-client-secret")
        // Wraps an already-built target client so this test does not depend on
        // CrossAppAccessFlowImpl's internal client-building — it only needs a real flow whose
        // start()/redeem() can genuinely fail.
        val target = CrossAppAccessTarget.wrapping(targetClient, scope = listOf("chat.read"))
        return CrossAppAccessFlow.create(idpClient, target).getOrThrow()
    }

    @Test
    fun classify_IdpExchangeFailure_AttributesToIdentityProvider() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 400, errorResponseJson("invalid_grant", "No trusted connection configured"))
            val flow = buildFlow(executor)

            val result = flow.start(SubjectAssertion.idToken("subject-id-token"))
            val error = result.exceptionOrNull()!!

            val classified = CrossAppAccessErrors.classify(error, subjectKind = SubjectKind.IDENTITY)

            assertEquals(FailureAttribution.IDENTITY_PROVIDER, classified.attribution)
            assertTrue(classified.message.contains("identity provider", ignoreCase = true))
            assertTrue(classified.message.contains("No trusted connection configured"))
            assertTrue(classified.message.contains("trust relationship", ignoreCase = true))
        }

    @Test
    fun classify_IdpExchangeFailureWithNonDefaultSubject_NamesSubjectKind() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 400, errorResponseJson("invalid_grant", "subject_token_type not accepted"))
            val flow = buildFlow(executor)

            val result = flow.start(SubjectAssertion.accessToken("subject-access-token"))
            val error = result.exceptionOrNull()!!

            val classified = CrossAppAccessErrors.classify(error, subjectKind = SubjectKind.ACCESS)

            assertEquals(FailureAttribution.IDENTITY_PROVIDER, classified.attribution)
            assertTrue(classified.message.contains("access", ignoreCase = true))
            assertTrue(classified.message.contains("identity token", ignoreCase = true))
        }

    @Test
    fun classify_TargetRedemptionFailure_AttributesToResourceServer() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            executor.enqueue("$targetIssuer/v1/token", 400, errorResponseJson("invalid_grant", "ID-JAG expired"))
            val flow = buildFlow(executor)

            val idJag = flow.start(SubjectAssertion.idToken("subject-id-token")).getOrThrow()
            val result = flow.redeem(idJag)
            val error = result.exceptionOrNull()!!

            val classified = CrossAppAccessErrors.classify(error)

            assertEquals(FailureAttribution.RESOURCE_SERVER, classified.attribution)
            assertTrue(classified.message.contains("resource authorization server", ignoreCase = true))
            assertTrue(classified.message.contains("ID-JAG expired"))
        }

    @Test
    fun classify_DeeplyWrappedCrossAppAccessException_StillAttributesCorrectly() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 400, errorResponseJson("invalid_grant", "No trusted connection configured"))
            val flow = buildFlow(executor)

            val genuine = flow.start(SubjectAssertion.idToken("subject-id-token")).exceptionOrNull()!!
            // Simulate the kind of wrapping a naive root-cause-first handler would otherwise
            // tunnel straight through, e.g. CompletableFuture's CompletionException.
            val wrapped = RuntimeException("wrapped", RuntimeException("intermediate", genuine))

            val classified = CrossAppAccessErrors.classify(wrapped)

            assertEquals(FailureAttribution.IDENTITY_PROVIDER, classified.attribution)
            assertTrue(classified.message.contains("No trusted connection configured"))
        }

    @Test
    fun classify_NoCrossAppAccessExceptionInChain_AttributesToConfiguration() =
        runTest {
            val executor = RecordingApiExecutor()
            val idpClient = buildTestOAuth2Client(idpIssuer, "idp-client-id", executor)
            // No credential configured on this target — create() fails before any network request
            // with a plain IllegalArgumentException, never a CrossAppAccessException.
            val target = CrossAppAccessTarget.forIssuer(targetIssuer) { scope = listOf("chat.read") }

            val error = CrossAppAccessFlow.create(idpClient, target).exceptionOrNull()!!

            val classified = CrossAppAccessErrors.classify(error)

            assertEquals(FailureAttribution.CONFIGURATION, classified.attribution)
            assertTrue(classified.message.contains("confidential client", ignoreCase = true))
        }
}
