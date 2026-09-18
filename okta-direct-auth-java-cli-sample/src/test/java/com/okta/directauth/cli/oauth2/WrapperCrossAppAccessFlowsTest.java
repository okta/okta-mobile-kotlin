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
package com.okta.directauth.cli.oauth2;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Verifies {@link WrapperCrossAppAccessFlows#normalizeScope}, the helper that stands between
 * {@link CrossAppAccessFlows#start}/{@link CrossAppAccessFlows#exchange}'s scope parameter and the
 * underlying JVM {@code CrossAppAccessFlow}, which documents {@code null} as "use the target's
 * configured default".
 *
 * <p>Tested directly rather than through {@link WrapperCrossAppAccessFlows#start}/{@link
 * WrapperCrossAppAccessFlows#exchange} themselves: reaching the buggy line requires {@link
 * com.okta.oauth2.kmp.jvm.CrossAppAccessFlow#create} to succeed, which needs a real {@code
 * OAuth2Client} — {@code null} short-circuits before ever touching {@code scope}.
 */
public class WrapperCrossAppAccessFlowsTest {

  @Test
  public void normalizeScope_Null_ReturnsNull() {
    assertThat(WrapperCrossAppAccessFlows.normalizeScope(null)).isNull();
  }

  @Test
  public void normalizeScope_Empty_ReturnsNull() {
    assertThat(WrapperCrossAppAccessFlows.normalizeScope(Collections.emptyList())).isNull();
  }

  @Test
  public void normalizeScope_NonEmpty_ReturnsUnchanged() {
    List<String> scope = List.of("chat.read");

    assertThat(WrapperCrossAppAccessFlows.normalizeScope(scope)).isSameInstanceAs(scope);
  }
}
