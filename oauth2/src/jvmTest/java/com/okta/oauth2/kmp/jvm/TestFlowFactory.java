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
package com.okta.oauth2.kmp.jvm;

import com.okta.oauth2.kmp.BrowserRedirectHandler;

/**
 * Factory for creating pre-built flow instances for Java wrapper tests.
 *
 * <p>Suspend interface delegates are provided by {@link FakeSuspendFlows}, a Kotlin bridge object.
 * Java test files cannot implement Kotlin suspend interfaces directly.
 */
public final class TestFlowFactory {

  private TestFlowFactory() {}

  /** Creates a {@link ResourceOwnerFlow} that always succeeds. */
  public static ResourceOwnerFlow createSuccessResourceOwnerFlow() {
    return new ResourceOwnerFlow(FakeSuspendFlows.successResourceOwnerDelegate());
  }

  /** Creates a {@link ResourceOwnerFlow} that always fails with {@code invalid_grant}. */
  public static ResourceOwnerFlow createFailingResourceOwnerFlow() {
    return new ResourceOwnerFlow(FakeSuspendFlows.failingResourceOwnerDelegate());
  }

  /** Creates a {@link DeviceAuthorizationFlow} that always succeeds. */
  public static DeviceAuthorizationFlow createSuccessDeviceAuthorizationFlow() {
    return new DeviceAuthorizationFlow(FakeSuspendFlows.successDeviceAuthorizationDelegate());
  }

  /** Creates a {@link DeviceAuthorizationFlow} that always fails. */
  public static DeviceAuthorizationFlow createFailingDeviceAuthorizationFlow() {
    return new DeviceAuthorizationFlow(FakeSuspendFlows.failingDeviceAuthorizationDelegate());
  }

  /** Creates a {@link TokenExchangeFlow} that always succeeds. */
  public static TokenExchangeFlow createSuccessTokenExchangeFlow() {
    return new TokenExchangeFlow(FakeSuspendFlows.successTokenExchangeDelegate());
  }

  /** Creates a {@link TokenExchangeFlow} that always fails with {@code invalid_grant}. */
  public static TokenExchangeFlow createFailingTokenExchangeFlow() {
    return new TokenExchangeFlow(FakeSuspendFlows.failingTokenExchangeDelegate());
  }

  /** Creates a {@link SessionTokenFlow} that always succeeds. */
  public static SessionTokenFlow createSuccessSessionTokenFlow() {
    return new SessionTokenFlow(FakeSuspendFlows.successSessionTokenDelegate());
  }

  /** Creates a {@link SessionTokenFlow} that always fails. */
  public static SessionTokenFlow createFailingSessionTokenFlow() {
    return new SessionTokenFlow(FakeSuspendFlows.failingSessionTokenDelegate());
  }

  /**
   * Creates a {@link RedirectEndSessionFlow} that always succeeds.
   *
   * @param redirectUri the post-logout redirect URI returned in the context.
   */
  public static RedirectEndSessionFlow createSuccessRedirectEndSessionFlow(String redirectUri) {
    return new RedirectEndSessionFlow(
        FakeSuspendFlows.successRedirectEndSessionDelegate(redirectUri));
  }

  /** Creates a {@link RedirectEndSessionFlow} that always fails. */
  public static RedirectEndSessionFlow createFailingRedirectEndSessionFlow() {
    return new RedirectEndSessionFlow(FakeSuspendFlows.failingRedirectEndSessionDelegate());
  }

  /**
   * Creates a fake {@link BrowserRedirectHandler} that immediately returns {@code redirectUri}.
   *
   * @param redirectUri the URI returned by {@code handleRedirect}.
   */
  public static BrowserRedirectHandler createFakeBrowserRedirectHandler(String redirectUri) {
    return FakeSuspendFlows.fakeBrowserRedirectHandler(redirectUri);
  }

  /** Creates an {@link AuthorizationCodeFlow} that always succeeds. */
  public static AuthorizationCodeFlow createSuccessAuthorizationCodeFlow() {
    return new AuthorizationCodeFlow(FakeSuspendFlows.successAuthorizationCodeDelegate());
  }

  /** Creates an {@link AuthorizationCodeFlow} that always fails. */
  public static AuthorizationCodeFlow createFailingAuthorizationCodeFlow() {
    return new AuthorizationCodeFlow(FakeSuspendFlows.failingAuthorizationCodeDelegate());
  }
}
