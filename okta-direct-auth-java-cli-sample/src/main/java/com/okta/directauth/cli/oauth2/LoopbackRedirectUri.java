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
   * <p>Deliberately stricter than "any loopback URI": {@code LocalhostBrowserRedirectHandler}'s
   * callback always answers back as the literal {@code http://localhost:<port><path>} — built
   * from the actual HTTP request line, which never carries a fragment, with a port that is always
   * explicit. A {@code signInRedirectUri} using {@code 127.0.0.1}/{@code ::1}, an omitted port, or
   * a fragment would pass this parse but then never match {@code
   * AuthorizationCodeFlowImpl.resume}'s {@code uri.startsWith(redirectUrl)} check once the browser
   * actually calls back — so those forms are rejected here instead, at startup, with an actionable
   * message.
   *
   * @param signInRedirectUri the loopback redirect URI (e.g. {@code
   *     http://localhost:8080/callback})
   * @return the parsed port and path
   * @throws IllegalArgumentException if {@code signInRedirectUri} is not a URI {@code
   *     LocalhostBrowserRedirectHandler} can round-trip
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
      if (!"localhost".equals(host)) {
        throw new IllegalArgumentException(
            "signInRedirectUri must use the literal loopback host \"localhost\" — the local HTTP"
                + " listener always answers back as \"localhost\", so 127.0.0.1/::1/other loopback"
                + " forms cannot be matched against it once the browser calls back; got: "
                + signInRedirectUri);
      }
      int port = uri.getPort();
      if (port == -1) {
        throw new IllegalArgumentException(
            "signInRedirectUri must include an explicit port — the local HTTP listener's callback"
                + " always includes one, so an omitted port cannot be matched against it; got: "
                + signInRedirectUri);
      }
      if (uri.getRawFragment() != null) {
        throw new IllegalArgumentException(
            "signInRedirectUri must not include a fragment — fragments are never sent to the local"
                + " HTTP listener, so they cannot be matched against its callback; got: "
                + signInRedirectUri);
      }
      String path = uri.getPath();
      return new LoopbackRedirectUri(port, (path == null || path.isEmpty()) ? "/" : path);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid signInRedirectUri: " + signInRedirectUri, e);
    }
  }
}
