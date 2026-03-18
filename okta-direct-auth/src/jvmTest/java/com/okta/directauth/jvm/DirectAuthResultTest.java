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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure Java tests for the DirectAuthResult type. */
public class DirectAuthResultTest {

  @Test
  public void success_IsSuccess() {
    DirectAuthResult<String> result = DirectAuthResult.success("hello");

    assertTrue(result.isSuccess());
    assertFalse(result.isFailure());
  }

  @Test
  public void success_GetOrThrow_ReturnsValue() {
    DirectAuthResult<String> result = DirectAuthResult.success("hello");

    assertEquals("hello", result.getOrThrow());
  }

  @Test
  public void success_GetOrNull_ReturnsValue() {
    DirectAuthResult<String> result = DirectAuthResult.success("hello");

    assertEquals("hello", result.getOrNull());
  }

  @Test
  public void success_ExceptionOrNull_ReturnsNull() {
    DirectAuthResult<String> result = DirectAuthResult.success("hello");

    assertNull(result.exceptionOrNull());
  }

  @Test
  public void failure_IsFailure() {
    DirectAuthResult<String> result = DirectAuthResult.failure(new IllegalArgumentException("bad"));

    assertTrue(result.isFailure());
    assertFalse(result.isSuccess());
  }

  @Test
  public void failure_GetOrNull_ReturnsNull() {
    DirectAuthResult<String> result = DirectAuthResult.failure(new IllegalArgumentException("bad"));

    assertNull(result.getOrNull());
  }

  @Test
  public void failure_ExceptionOrNull_ReturnsException() {
    IllegalArgumentException ex = new IllegalArgumentException("bad");
    DirectAuthResult<String> result = DirectAuthResult.failure(ex);

    assertSame(ex, result.exceptionOrNull());
  }

  @Test(expected = IllegalArgumentException.class)
  public void failure_GetOrThrow_ThrowsException() {
    DirectAuthResult<String> result = DirectAuthResult.failure(new IllegalArgumentException("bad"));

    result.getOrThrow();
  }

  @Test
  public void success_WithNullValue_IsSuccess() {
    DirectAuthResult<String> result = DirectAuthResult.success(null);

    assertTrue(result.isSuccess());
    assertNull(result.getOrThrow());
  }
}
