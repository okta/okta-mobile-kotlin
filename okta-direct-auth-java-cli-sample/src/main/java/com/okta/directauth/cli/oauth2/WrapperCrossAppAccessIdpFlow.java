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
import com.okta.authfoundation.client.kmp.OAuth2Client;
import com.okta.oauth2.kmp.LocalhostBrowserRedirectHandler;
import com.okta.oauth2.kmp.jvm.AuthorizationCodeFlow;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Production implementation of {@link CrossAppAccessIdpFlow}, backed by the OAuth2 module's JVM
 * {@link AuthorizationCodeFlow} wrapper — the same building block {@code WrapperOAuth2Flows} uses
 * for the primary app's own Browser Sign-In, here bound to Cross App Access's own, separate IdP
 * client instead.
 *
 * <p>Reuses the primary app's {@code desktopSignInRedirectUri}/{@code signInRedirectUri} rather
 * than a dedicated one: the redirect URI only needs to be registered on whichever Okta app
 * integration is being authenticated against, and Okta allows the same URI to be registered on more
 * than one app — see the README for the admin-console setup.
 */
public final class WrapperCrossAppAccessIdpFlow implements CrossAppAccessIdpFlow {
  private final OAuth2Client idpClient;
  private final List<String> scope;
  private final String redirectUrl;
  private final int redirectPort;
  private final String redirectPath;

  /**
   * Creates a {@link WrapperCrossAppAccessIdpFlow}.
   *
   * @param idpClient Cross App Access's own, dedicated requesting-app client
   * @param scope the OAuth2 scopes to request at sign-in
   * @param signInRedirectUri the loopback redirect URI (e.g. {@code
   *     http://localhost:8080/callback}), reused from the primary app's own configuration
   * @throws IllegalArgumentException if {@code signInRedirectUri} is not a valid http loopback URI
   */
  public WrapperCrossAppAccessIdpFlow(
      OAuth2Client idpClient, List<String> scope, String signInRedirectUri) {
    this.idpClient = idpClient;
    this.scope = scope;
    this.redirectUrl = signInRedirectUri;

    try {
      URI uri = new URI(signInRedirectUri);
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (!"http".equalsIgnoreCase(scheme)) {
        throw new IllegalArgumentException(
            "signInRedirectUri must use the http scheme; got: " + signInRedirectUri);
      }
      if (host == null || !isLoopbackHost(host)) {
        throw new IllegalArgumentException(
            "signInRedirectUri must be a loopback address (localhost / 127.0.0.1 / ::1); got: "
                + signInRedirectUri);
      }
      int port = uri.getPort();
      this.redirectPort = (port == -1) ? 80 : port;
      String path = uri.getPath();
      this.redirectPath = (path == null || path.isEmpty()) ? "/" : path;
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid signInRedirectUri: " + signInRedirectUri, e);
    }
  }

  @Override
  public CompletableFuture<TokenInfo> browserSignIn() {
    LocalhostBrowserRedirectHandler handler =
        new LocalhostBrowserRedirectHandler(redirectPort, redirectPath);
    AuthorizationCodeFlow flow = new AuthorizationCodeFlow(idpClient);
    return flow.start(redirectUrl, handler, scope, Collections.emptyMap())
        .whenComplete((r, t) -> closeQuietly(flow));
  }

  @Override
  public void close() {
    // No retained state between calls — nothing to release here beyond what browserSignIn()
    // already closes itself on completion.
  }

  private static boolean isLoopbackHost(String host) {
    // URI.getHost() strips brackets from IPv6 addresses (returns "::1" not "[::1]").
    return host.equals("localhost")
        || host.equals("127.0.0.1")
        || host.equals("::1")
        || host.equals("[::1]"); // guard against JDK implementations that retain brackets
  }

  private static void closeQuietly(AutoCloseable c) {
    try {
      c.close();
    } catch (Exception ignored) {
      // Best-effort: cancel any remaining coroutine scope.
    }
  }
}
