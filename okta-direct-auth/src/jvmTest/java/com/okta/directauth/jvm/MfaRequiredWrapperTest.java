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
package com.okta.directauth.jvm;

import static org.junit.Assert.assertNotNull;

import com.okta.authfoundation.ChallengeGrantType;
import com.okta.directauth.model.PrimaryFactor;
import com.okta.directauth.model.SecondaryFactor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

/** Tests verifying MfaRequired wrapper API is usable from Java. */
public class MfaRequiredWrapperTest {

  @Test
  public void challengeAsync_WithSecondaryFactor_ReturnsCompletableFuture() {
    MfaRequired mfaRequired = new MfaRequired(TestStateFactory.createMfaRequired());
    SecondaryFactor factor = new PrimaryFactor.Otp("123456");

    CompletableFuture<?> future = mfaRequired.challengeAsync(factor);

    assertNotNull(future);
  }

  @Test
  public void challengeAsync_WithChallengeTypes_ReturnsCompletableFuture() {
    MfaRequired mfaRequired = new MfaRequired(TestStateFactory.createMfaRequired());
    SecondaryFactor factor = new PrimaryFactor.Otp("123456");
    List<ChallengeGrantType> types = List.of(ChallengeGrantType.OtpMfa.INSTANCE);

    CompletableFuture<?> future = mfaRequired.challengeAsync(factor, types);

    assertNotNull(future);
  }

  @Test
  public void resumeAsync_WithSecondaryFactor_ReturnsCompletableFuture() {
    MfaRequired mfaRequired = new MfaRequired(TestStateFactory.createMfaRequired());
    SecondaryFactor factor = new PrimaryFactor.Otp("123456");

    CompletableFuture<?> future = mfaRequired.resumeAsync(factor);

    assertNotNull(future);
  }

  @Test
  public void resumeAsync_WithChallengeTypes_ReturnsCompletableFuture() {
    MfaRequired mfaRequired = new MfaRequired(TestStateFactory.createMfaRequired());
    SecondaryFactor factor = new PrimaryFactor.Otp("123456");
    List<ChallengeGrantType> types = List.of(ChallengeGrantType.OtpMfa.INSTANCE);

    CompletableFuture<?> future = mfaRequired.resumeAsync(factor, types);

    assertNotNull(future);
  }
}
