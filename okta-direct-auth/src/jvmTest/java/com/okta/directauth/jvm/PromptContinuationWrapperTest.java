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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.CompletableFuture;
import org.junit.Test;

/** Tests verifying PromptContinuation wrapper API is usable from Java. */
public class PromptContinuationWrapperTest {

  @Test
  public void expirationInSeconds_DelegatesToDelegate() {
    PromptContinuation continuation = new PromptContinuation(TestStateFactory.createPrompt(120));

    assertEquals(120, continuation.getExpirationInSeconds());
  }

  @Test
  public void proceedAsync_WithCode_ReturnsCompletableFuture() {
    PromptContinuation continuation = new PromptContinuation(TestStateFactory.createPrompt());

    CompletableFuture<?> future = continuation.proceedAsync("123456");

    assertNotNull(future);
  }
}
