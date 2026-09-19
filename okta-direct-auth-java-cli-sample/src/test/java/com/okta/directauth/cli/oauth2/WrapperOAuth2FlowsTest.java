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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import com.okta.authfoundation.client.kmp.OAuth2Client;
import java.util.List;
import org.junit.Test;

public class WrapperOAuth2FlowsTest {

  // ── Valid loopback URIs ───────────────────────────────────────────────────────

  @Test
  public void signInRedirectUri_Localhost_Accepted() {
    WrapperOAuth2Flows flows =
        new WrapperOAuth2Flows(mockClient(), scopes(), "http://localhost:8080/callback");
    flows.close();
  }

  @Test
  public void signInRedirectUri_CustomPath_Accepted() {
    WrapperOAuth2Flows flows =
        new WrapperOAuth2Flows(mockClient(), scopes(), "http://localhost:8080/oauth/callback");
    flows.close();
  }

  @Test
  public void signInRedirectUri_EmptyPath_DefaultsToSlash() {
    // URI with no path component should not throw.
    WrapperOAuth2Flows flows =
        new WrapperOAuth2Flows(mockClient(), scopes(), "http://localhost:8080");
    flows.close();
  }

  // ── Invalid URIs ─────────────────────────────────────────────────────────────

  @Test
  public void signInRedirectUri_HttpsScheme_Rejected() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WrapperOAuth2Flows(mockClient(), scopes(), "https://localhost:8080/callback"));
    assertThat(ex.getMessage()).contains("http scheme");
  }

  @Test
  public void signInRedirectUri_NonLoopbackHost_Rejected() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WrapperOAuth2Flows(mockClient(), scopes(), "http://example.com:8080/callback"));
    assertThat(ex.getMessage()).contains("loopback");
  }

  @Test
  public void signInRedirectUri_PublicIp_Rejected() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WrapperOAuth2Flows(mockClient(), scopes(), "http://192.168.1.1:8080/callback"));
    assertThat(ex.getMessage()).contains("loopback");
  }

  @Test
  public void signInRedirectUri_LoopbackIp_Rejected() {
    // The local HTTP listener's callback always answers back as "http://localhost:<port>...",
    // never "http://127.0.0.1:...", so this form can never match at resume() time even though
    // 127.0.0.1 is itself a loopback address.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new WrapperOAuth2Flows(mockClient(), scopes(), "http://127.0.0.1:9090/auth"));
    assertThat(ex.getMessage()).contains("localhost");
  }

  @Test
  public void signInRedirectUri_Ipv6Loopback_Rejected() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new WrapperOAuth2Flows(mockClient(), scopes(), "http://[::1]:8080/callback"));
    assertThat(ex.getMessage()).contains("localhost");
  }

  @Test
  public void signInRedirectUri_OmittedPort_Rejected() {
    // The local HTTP listener's callback always includes an explicit port, so an omitted port
    // (implicitly 80) could never match at resume() time.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new WrapperOAuth2Flows(mockClient(), scopes(), "http://localhost/callback"));
    assertThat(ex.getMessage()).contains("port");
  }

  @Test
  public void signInRedirectUri_Fragment_Rejected() {
    // Fragments are never sent to the local HTTP listener, so they could never match at
    // resume() time.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WrapperOAuth2Flows(
                    mockClient(), scopes(), "http://localhost:8080/callback#fragment"));
    assertThat(ex.getMessage()).contains("fragment");
  }

  @Test
  public void signInRedirectUri_InvalidSyntax_Rejected() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new WrapperOAuth2Flows(mockClient(), scopes(), "not a valid uri ::"));
    assertThat(ex.getMessage()).contains("Invalid signInRedirectUri");
  }

  @Test
  public void signInRedirectUri_NullHost_Rejected() {
    // Opaque URIs have no host component.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new WrapperOAuth2Flows(mockClient(), scopes(), "http:callback"));
    assertThat(ex.getMessage()).isNotEmpty();
  }

  // ── Resource lifecycle ────────────────────────────────────────────────────────

  @Test
  public void close_IdempotentOnEmptyState() {
    WrapperOAuth2Flows flows =
        new WrapperOAuth2Flows(mockClient(), scopes(), "http://localhost:8080/callback");
    flows.close();
    // Second close must not throw.
    flows.close();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private static OAuth2Client mockClient() {
    return mock(OAuth2Client.class);
  }

  private static List<String> scopes() {
    return List.of("openid", "profile", "email");
  }
}
