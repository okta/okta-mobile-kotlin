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

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A validated http loopback redirect URI's port and path, shared by {@link WrapperOAuth2Flows} and
 * {@link WrapperCrossAppAccessIdpFlow} — both build a {@code LocalhostBrowserRedirectHandler} from
 * the same kind of URI, for two independent app registrations.
 */
final class LoopbackRedirectUri {
  private final int port;
  private final String path;

  private LoopbackRedirectUri(int port, String path) {
    this.port = port;
    this.path = path;
  }

  int getPort() {
    return port;
  }

  String getPath() {
    return path;
  }

  /**
   * Parses and validates {@code signInRedirectUri}.
   *
   * @param signInRedirectUri the loopback redirect URI (e.g. {@code
   *     http://localhost:8080/callback})
   * @return the parsed port and path
   * @throws IllegalArgumentException if {@code signInRedirectUri} is not a valid http loopback URI
   */
  static LoopbackRedirectUri parse(String signInRedirectUri) {
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
      String path = uri.getPath();
      return new LoopbackRedirectUri(
          port == -1 ? 80 : port, (path == null || path.isEmpty()) ? "/" : path);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid signInRedirectUri: " + signInRedirectUri, e);
    }
  }

  private static boolean isLoopbackHost(String host) {
    // URI.getHost() strips brackets from IPv6 addresses (returns "::1" not "[::1]").
    return host.equals("localhost")
        || host.equals("127.0.0.1")
        || host.equals("::1")
        || host.equals("[::1]"); // guard against JDK implementations that retain brackets
  }
}
