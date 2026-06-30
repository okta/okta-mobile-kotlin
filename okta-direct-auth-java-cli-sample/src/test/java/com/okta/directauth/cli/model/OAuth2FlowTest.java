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

import org.junit.Test;

public class OAuth2FlowTest {

  @Test
  public void resourceOwner_RequiresCredentials() {
    assertThat(OAuth2Flow.RESOURCE_OWNER.requiresCredentials()).isTrue();
    assertThat(OAuth2Flow.RESOURCE_OWNER.requiresTokens()).isFalse();
    assertThat(OAuth2Flow.RESOURCE_OWNER.requiresSessionToken()).isFalse();
    assertThat(OAuth2Flow.RESOURCE_OWNER.getLabel()).isNotEmpty();
  }

  @Test
  public void deviceAuthorization_RequiresNoInput() {
    assertThat(OAuth2Flow.DEVICE_AUTHORIZATION.requiresCredentials()).isFalse();
    assertThat(OAuth2Flow.DEVICE_AUTHORIZATION.requiresTokens()).isFalse();
    assertThat(OAuth2Flow.DEVICE_AUTHORIZATION.requiresSessionToken()).isFalse();
  }

  @Test
  public void browserSignIn_RequiresNoInput() {
    assertThat(OAuth2Flow.BROWSER_SIGN_IN.requiresCredentials()).isFalse();
    assertThat(OAuth2Flow.BROWSER_SIGN_IN.requiresTokens()).isFalse();
    assertThat(OAuth2Flow.BROWSER_SIGN_IN.requiresSessionToken()).isFalse();
  }

  @Test
  public void tokenExchange_RequiresTokens() {
    assertThat(OAuth2Flow.TOKEN_EXCHANGE.requiresTokens()).isTrue();
    assertThat(OAuth2Flow.TOKEN_EXCHANGE.requiresCredentials()).isFalse();
    assertThat(OAuth2Flow.TOKEN_EXCHANGE.requiresSessionToken()).isFalse();
  }

  @Test
  public void sessionToken_RequiresSessionToken() {
    assertThat(OAuth2Flow.SESSION_TOKEN.requiresSessionToken()).isTrue();
    assertThat(OAuth2Flow.SESSION_TOKEN.requiresCredentials()).isFalse();
    assertThat(OAuth2Flow.SESSION_TOKEN.requiresTokens()).isFalse();
  }

  @Test
  public void allFlows_HaveLabels() {
    for (OAuth2Flow flow : OAuth2Flow.values()) {
      assertThat(flow.getLabel()).isNotEmpty();
    }
  }

  @Test
  public void fiveFlows_Defined() {
    assertThat(OAuth2Flow.values()).hasLength(5);
  }
}
