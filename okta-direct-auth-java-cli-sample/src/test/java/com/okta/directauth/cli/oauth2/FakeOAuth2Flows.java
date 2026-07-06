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

import com.okta.authfoundation.client.TokenInfo;
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext;
import java.util.concurrent.CompletableFuture;

/**
 * Test double for {@link OAuth2Flows}.
 *
 * <p>Returns pre-configured futures so tests can exercise success, error, and timeout paths without
 * network access. Records the last invoked method and arguments for assertion.
 */
public final class FakeOAuth2Flows implements OAuth2Flows {
  private CompletableFuture<TokenInfo> tokenResult = new CompletableFuture<>();
  private CompletableFuture<DeviceAuthorizationFlowContext> deviceStartResult =
      new CompletableFuture<>();
  private boolean closed = false;

  // Last invocation tracking
  private String lastMethod;
  private String lastUsername;
  private String lastPassword;
  private String lastIdToken;
  private String lastDeviceSecret;
  private String lastSessionToken;
  private DeviceAuthorizationFlowContext lastDeviceContext;

  /** Configures the fake to complete token futures successfully with the given token. */
  public void succeedWith(TokenInfo token) {
    tokenResult = CompletableFuture.completedFuture(token);
  }

  /** Configures the fake to fail token futures with the given exception. */
  public void failWith(Throwable error) {
    tokenResult = CompletableFuture.failedFuture(error);
  }

  /** Configures the fake device start to succeed with the given context. */
  public void deviceStartSucceedWith(DeviceAuthorizationFlowContext context) {
    deviceStartResult = CompletableFuture.completedFuture(context);
  }

  /** Configures the fake device start to fail. */
  public void deviceStartFailWith(Throwable error) {
    deviceStartResult = CompletableFuture.failedFuture(error);
  }

  /** Returns the name of the last method called on this fake. */
  public String getLastMethod() {
    return lastMethod;
  }

  public String getLastUsername() {
    return lastUsername;
  }

  public String getLastPassword() {
    return lastPassword;
  }

  public String getLastIdToken() {
    return lastIdToken;
  }

  public String getLastDeviceSecret() {
    return lastDeviceSecret;
  }

  public String getLastSessionToken() {
    return lastSessionToken;
  }

  public DeviceAuthorizationFlowContext getLastDeviceContext() {
    return lastDeviceContext;
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public CompletableFuture<TokenInfo> resourceOwner(String username, String password) {
    lastMethod = "resourceOwner";
    lastUsername = username;
    lastPassword = password;
    return tokenResult;
  }

  @Override
  public CompletableFuture<DeviceAuthorizationFlowContext> deviceStart() {
    lastMethod = "deviceStart";
    return deviceStartResult;
  }

  @Override
  public CompletableFuture<TokenInfo> deviceResume(DeviceAuthorizationFlowContext context) {
    lastMethod = "deviceResume";
    lastDeviceContext = context;
    return tokenResult;
  }

  @Override
  public CompletableFuture<TokenInfo> browserSignIn() {
    lastMethod = "browserSignIn";
    return tokenResult;
  }

  @Override
  public CompletableFuture<TokenInfo> tokenExchange(String idToken, String deviceSecret) {
    lastMethod = "tokenExchange";
    lastIdToken = idToken;
    lastDeviceSecret = deviceSecret;
    return tokenResult;
  }

  @Override
  public CompletableFuture<TokenInfo> sessionToken(String sessionToken) {
    lastMethod = "sessionToken";
    lastSessionToken = sessionToken;
    return tokenResult;
  }

  @Override
  public void close() {
    closed = true;
  }
}
