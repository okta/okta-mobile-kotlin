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
import static org.junit.Assert.assertNull;

import java.util.concurrent.CompletableFuture;
import org.junit.Test;

/** Tests verifying TransferContinuation wrapper API is usable from Java. */
public class TransferContinuationWrapperTest {

  @Test
  public void expirationInSeconds_DelegatesToDelegate() {
    TransferContinuation continuation =
        new TransferContinuation(TestStateFactory.createTransfer(180, null));

    assertEquals(180, continuation.getExpirationInSeconds());
  }

  @Test
  public void bindingCode_ReturnsCode() {
    TransferContinuation continuation =
        new TransferContinuation(TestStateFactory.createTransfer(300, "XYZ789"));

    assertEquals("XYZ789", continuation.getBindingCode());
  }

  @Test
  public void bindingCode_ReturnsNull_WhenNotPresent() {
    TransferContinuation continuation =
        new TransferContinuation(TestStateFactory.createTransfer(300, null));

    assertNull(continuation.getBindingCode());
  }

  @Test
  public void proceedAsync_ReturnsCompletableFuture() {
    TransferContinuation continuation = new TransferContinuation(TestStateFactory.createTransfer());

    CompletableFuture<?> future = continuation.proceedAsync();

    assertNotNull(future);
  }
}
