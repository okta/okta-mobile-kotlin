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
package com.okta.directauth.cli.model;

import static com.google.common.truth.Truth.assertThat;

import com.okta.oauth2.kmp.CrossAppAccessException;
import java.util.concurrent.CompletionException;
import org.junit.Test;

/**
 * Verifies {@link CrossAppAccessFailure#classify} attributes every failure to the server that
 * produced it, rather than to a generic transport error.
 *
 * <p>{@link CrossAppAccessException.IdpExchangeFailed} and {@link
 * CrossAppAccessException.TargetRedemptionFailed} declare Kotlin {@code internal} constructors,
 * which restrict construction from other Kotlin modules at compile time — but the JVM has no
 * "module-internal" access level, so Kotlin compiles {@code internal} constructors as plain {@code
 * public} in bytecode (verified directly against the compiled class). This is documented
 * Kotlin/Java interop behavior, not a workaround: it is the only way to construct a genuine
 * instance of the type this classifier's core contract is about, from a pure-Java test, without
 * hand-rolling a fake {@code suspend fun} caller and importing Kotlin coroutine ABI types into this
 * Java-only sample's tests.
 */
public class CrossAppAccessFailureTest {

  @Test
  public void classify_IdpExchangeFailedInChain_AttributesToIdentityProvider() {
    CrossAppAccessException error =
        new CrossAppAccessException.IdpExchangeFailed(
            "wrapper message", new RuntimeException("No trusted connection configured"));

    CrossAppAccessFailure result = CrossAppAccessFailure.classify(error, SubjectKind.IDENTITY);

    assertThat(result.getAttribution())
        .isEqualTo(CrossAppAccessFailure.Attribution.IDENTITY_PROVIDER);
    assertThat(result.getMessage()).contains("identity provider");
    assertThat(result.getMessage()).contains("No trusted connection configured");
    assertThat(result.getMessage()).contains("trust relationship");
  }

  @Test
  public void classify_TargetRedemptionFailedInChain_AttributesToResourceServer() {
    CrossAppAccessException error =
        new CrossAppAccessException.TargetRedemptionFailed(
            "wrapper message", new RuntimeException("ID-JAG expired"));

    CrossAppAccessFailure result = CrossAppAccessFailure.classify(error);

    assertThat(result.getAttribution())
        .isEqualTo(CrossAppAccessFailure.Attribution.RESOURCE_SERVER);
    assertThat(result.getMessage()).contains("resource authorization server");
    assertThat(result.getMessage()).contains("ID-JAG expired");
  }

  @Test
  public void classify_DeeplyWrappedInCompletionException_StillAttributesCorrectly() {
    CrossAppAccessException genuine =
        new CrossAppAccessException.IdpExchangeFailed(
            "wrapper message", new RuntimeException("No trusted connection configured"));
    // Simulates exactly what CompletableFuture does to an exceptional completion — the shape this
    // classifier must see through rather than the naive root-cause-first handler it replaces.
    CompletionException wrapped = new CompletionException(genuine);

    CrossAppAccessFailure result = CrossAppAccessFailure.classify(wrapped);

    assertThat(result.getAttribution())
        .isEqualTo(CrossAppAccessFailure.Attribution.IDENTITY_PROVIDER);
    assertThat(result.getMessage()).contains("No trusted connection configured");
  }

  @Test
  public void classify_NonDefaultSubjectKindRefused_NamesKindAndSuggestsIdentity() {
    CrossAppAccessException error =
        new CrossAppAccessException.IdpExchangeFailed(
            "wrapper message", new RuntimeException("subject_token_type not accepted"));

    CrossAppAccessFailure result = CrossAppAccessFailure.classify(error, SubjectKind.ACCESS);

    assertThat(result.getMessage()).contains("access");
    assertThat(result.getMessage()).contains("identity token");
  }

  @Test
  public void classify_NoCrossAppAccessExceptionInChain_AttributesToConfiguration() {
    IllegalArgumentException error =
        new IllegalArgumentException(
            "The target resource authorization server has no client secret or client assertion "
                + "provider configured. Cross App Access requires a confidential client at both steps.");

    CrossAppAccessFailure result = CrossAppAccessFailure.classify(error);

    assertThat(result.getAttribution()).isEqualTo(CrossAppAccessFailure.Attribution.CONFIGURATION);
    assertThat(result.getMessage()).contains("confidential client");
  }
}
