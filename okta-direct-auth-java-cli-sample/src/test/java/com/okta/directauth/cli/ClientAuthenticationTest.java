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
package com.okta.directauth.cli;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.okta.authfoundation.client.ClientAssertion;
import com.okta.authfoundation.client.ClientAssertionProvider;
import com.okta.authfoundation.client.jvm.OAuth2ClientBuilder;
import com.okta.directauth.jvm.DirectAuthenticationFlowBuilder;
import com.okta.oauth2.kmp.jvm.CrossAppAccessTargetBuilder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Properties;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ClientAuthenticationTest {

  private static final String CLIENT_ID = "test-client-id";

  @Test
  public void parsePkcs8PrivateKey_RsaPem_ParsesToEquivalentKey() throws NoSuchAlgorithmException {
    PrivateKey originalKey = generateRsaKeyPair().getPrivate();

    PrivateKey parsedKey =
        ClientAuthentication.parsePkcs8PrivateKey(
            toPem(originalKey), "clientAssertionPrivateKeyPem");

    assertThat(parsedKey.getAlgorithm()).isEqualTo("RSA");
    assertThat(parsedKey.getEncoded()).isEqualTo(originalKey.getEncoded());
  }

  @Test
  public void parsePkcs8PrivateKey_NotAValidKey_ThrowsIllegalStateExceptionNamingTheProperty() {
    String garbagePem = "-----BEGIN PRIVATE KEY-----\nbm90LWEta2V5\n-----END PRIVATE KEY-----";

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () ->
                ClientAuthentication.parsePkcs8PrivateKey(
                    garbagePem, "xaaTargetClientAssertionPrivateKeyPem"));

    // A dev with more than one PEM property configured (this sample has three) must be pointed
    // at the one that's actually invalid, not always told about clientAssertionPrivateKeyPem.
    assertThat(e).hasMessageThat().contains("xaaTargetClientAssertionPrivateKeyPem");
  }

  @Test
  public void buildClientAssertionJwt_ProducesClaimsVerifiableWithPublicKey()
      throws NoSuchAlgorithmException {
    KeyPair keyPair = generateRsaKeyPair();
    String audience = "https://example.okta.com/oauth2/default/v1/token";

    String jwt =
        ClientAuthentication.buildClientAssertionJwt(CLIENT_ID, audience, keyPair.getPrivate());

    Claims claims =
        Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(jwt).getPayload();
    assertThat(claims.getIssuer()).isEqualTo(CLIENT_ID);
    assertThat(claims.getSubject()).isEqualTo(CLIENT_ID);
    assertThat(claims.getAudience()).containsExactly(audience);
    assertThat(claims.getExpiration()).isNotNull();
  }

  @Test
  public void configure_WithClientSecretOnly_SetsClientSecretOnly() {
    OAuth2ClientBuilder builder = mock(OAuth2ClientBuilder.class);
    Properties properties = new Properties();
    properties.setProperty("clientSecret", "test-secret");

    ClientAuthentication.configure(builder, CLIENT_ID, properties);

    verify(builder).setClientSecret("test-secret");
    verify(builder, never()).setClientAssertionProvider(any());
  }

  @Test
  public void configure_WithClientAssertionPemOnly_RegistersProviderThatSignsFreshJwtPerCall()
      throws NoSuchAlgorithmException {
    OAuth2ClientBuilder builder = mock(OAuth2ClientBuilder.class);
    KeyPair keyPair = generateRsaKeyPair();
    Properties properties = new Properties();
    properties.setProperty("clientAssertionPrivateKeyPem", toPem(keyPair.getPrivate()));

    ClientAuthentication.configure(builder, CLIENT_ID, properties);

    verify(builder, never()).setClientSecret(any());
    ArgumentCaptor<ClientAssertionProvider> providerCaptor =
        ArgumentCaptor.forClass(ClientAssertionProvider.class);
    verify(builder).setClientAssertionProvider(providerCaptor.capture());
    ClientAssertionProvider provider = providerCaptor.getValue();

    ClientAssertion first = provider.provide("https://example.okta.com/oauth2/default/v1/par");
    ClientAssertion second = provider.provide("https://example.okta.com/oauth2/default/v1/token");

    assertThat(first.getType()).isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    assertThat(second.getType())
        .isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    // Each call must be freshly signed (unique jti) and scoped to the audience it was asked for.
    assertThat(first.getAssertion()).isNotEqualTo(second.getAssertion());
    Claims firstClaims =
        Jwts.parser()
            .verifyWith(keyPair.getPublic())
            .build()
            .parseSignedClaims(first.getAssertion())
            .getPayload();
    assertThat(firstClaims.getAudience())
        .containsExactly("https://example.okta.com/oauth2/default/v1/par");
    Claims secondClaims =
        Jwts.parser()
            .verifyWith(keyPair.getPublic())
            .build()
            .parseSignedClaims(second.getAssertion())
            .getPayload();
    assertThat(secondClaims.getAudience())
        .containsExactly("https://example.okta.com/oauth2/default/v1/token");
  }

  @Test
  public void configure_WithBothClientSecretAndAssertionPem_PrefersAssertionProvider()
      throws NoSuchAlgorithmException {
    OAuth2ClientBuilder builder = mock(OAuth2ClientBuilder.class);
    Properties properties = new Properties();
    properties.setProperty("clientSecret", "test-secret");
    properties.setProperty(
        "clientAssertionPrivateKeyPem", toPem(generateRsaKeyPair().getPrivate()));

    ClientAuthentication.configure(builder, CLIENT_ID, properties);

    verify(builder).setClientAssertionProvider(any());
    verify(builder, never()).setClientSecret(any());
  }

  @Test
  public void configure_WithNeitherConfigured_LeavesBuilderUntouched() {
    OAuth2ClientBuilder builder = mock(OAuth2ClientBuilder.class);

    ClientAuthentication.configure(builder, CLIENT_ID, new Properties());

    verifyNoMoreInteractions(builder);
  }

  @Test
  public void configure_DirectAuthBuilder_WithClientSecretOnly_SetsClientSecretOnly() {
    DirectAuthenticationFlowBuilder builder = mock(DirectAuthenticationFlowBuilder.class);
    Properties properties = new Properties();
    properties.setProperty("clientSecret", "test-secret");

    ClientAuthentication.configure(builder, CLIENT_ID, properties);

    verify(builder).setClientSecret("test-secret");
    verify(builder, never()).setClientAssertionProvider(any());
  }

  @Test
  public void
      configure_DirectAuthBuilder_WithClientAssertionPemOnly_RegistersProviderThatSignsFreshJwtPerCall()
          throws NoSuchAlgorithmException {
    DirectAuthenticationFlowBuilder builder = mock(DirectAuthenticationFlowBuilder.class);
    KeyPair keyPair = generateRsaKeyPair();
    Properties properties = new Properties();
    properties.setProperty("clientAssertionPrivateKeyPem", toPem(keyPair.getPrivate()));

    ClientAuthentication.configure(builder, CLIENT_ID, properties);

    verify(builder, never()).setClientSecret(any());
    ArgumentCaptor<ClientAssertionProvider> providerCaptor =
        ArgumentCaptor.forClass(ClientAssertionProvider.class);
    verify(builder).setClientAssertionProvider(providerCaptor.capture());
    ClientAssertionProvider provider = providerCaptor.getValue();

    ClientAssertion first = provider.provide("https://example.okta.com/oauth2/default/v1/token");
    ClientAssertion second =
        provider.provide("https://example.okta.com/oauth2/default/v1/challenge");

    // Each call must be freshly signed (unique jti) and scoped to the audience it was asked for.
    assertThat(first.getAssertion()).isNotEqualTo(second.getAssertion());
    Claims firstClaims =
        Jwts.parser()
            .verifyWith(keyPair.getPublic())
            .build()
            .parseSignedClaims(first.getAssertion())
            .getPayload();
    assertThat(firstClaims.getAudience())
        .containsExactly("https://example.okta.com/oauth2/default/v1/token");
    Claims secondClaims =
        Jwts.parser()
            .verifyWith(keyPair.getPublic())
            .build()
            .parseSignedClaims(second.getAssertion())
            .getPayload();
    assertThat(secondClaims.getAudience())
        .containsExactly("https://example.okta.com/oauth2/default/v1/challenge");
  }

  @Test
  public void configure_DirectAuthBuilder_WithNeitherConfigured_LeavesBuilderUntouched() {
    DirectAuthenticationFlowBuilder builder = mock(DirectAuthenticationFlowBuilder.class);

    ClientAuthentication.configure(builder, CLIENT_ID, new Properties());

    verifyNoMoreInteractions(builder);
  }

  @Test
  public void configureTarget_ReadsTargetScopedPropertiesOnly() {
    CrossAppAccessTargetBuilder builder = mock(CrossAppAccessTargetBuilder.class);
    Properties properties = new Properties();
    properties.setProperty("clientSecret", "primary-secret");
    properties.setProperty("xaaIdpClientSecret", "idp-secret");
    properties.setProperty("xaaTargetClientSecret", "target-secret");

    ClientAuthentication.configureTarget(builder, "target-client-id", properties);

    verify(builder).setClientSecret("target-secret");
    verify(builder, never()).setClientAssertionProvider(any());
  }

  @Test
  public void configureTarget_WithNeitherConfigured_LeavesBuilderUntouched() {
    CrossAppAccessTargetBuilder builder = mock(CrossAppAccessTargetBuilder.class);

    ClientAuthentication.configureTarget(builder, "target-client-id", new Properties());

    verifyNoMoreInteractions(builder);
  }

  @Test
  public void configureIdpClient_ReadsIdpScopedPropertiesOnly() {
    OAuth2ClientBuilder builder = mock(OAuth2ClientBuilder.class);
    Properties properties = new Properties();
    properties.setProperty("clientSecret", "primary-secret");
    properties.setProperty("xaaTargetClientSecret", "target-secret");
    properties.setProperty("xaaIdpClientSecret", "idp-secret");

    ClientAuthentication.configureIdpClient(builder, "idp-client-id", properties);

    verify(builder).setClientSecret("idp-secret");
    verify(builder, never()).setClientAssertionProvider(any());
  }

  @Test
  public void configureIdpClient_WithNeitherConfigured_LeavesBuilderUntouched() {
    OAuth2ClientBuilder builder = mock(OAuth2ClientBuilder.class);

    ClientAuthentication.configureIdpClient(builder, "idp-client-id", new Properties());

    verifyNoMoreInteractions(builder);
  }

  @Test
  public void hasTargetCredential_ClientSecretSet_ReturnsTrue() {
    Properties properties = new Properties();
    properties.setProperty("xaaTargetClientSecret", "target-secret");

    assertThat(ClientAuthentication.hasTargetCredential(properties)).isTrue();
  }

  @Test
  public void hasTargetCredential_AssertionPemSet_ReturnsTrue() {
    Properties properties = new Properties();
    properties.setProperty("xaaTargetClientAssertionPrivateKeyPem", "-----BEGIN PRIVATE KEY-----");

    assertThat(ClientAuthentication.hasTargetCredential(properties)).isTrue();
  }

  @Test
  public void hasTargetCredential_OnlyBlankValues_ReturnsFalse() {
    Properties properties = new Properties();
    properties.setProperty("xaaTargetClientSecret", "   ");
    properties.setProperty("xaaTargetClientAssertionPrivateKeyPem", "");

    assertThat(ClientAuthentication.hasTargetCredential(properties)).isFalse();
  }

  @Test
  public void hasTargetCredential_NeitherSet_ReturnsFalse() {
    assertThat(ClientAuthentication.hasTargetCredential(new Properties())).isFalse();
  }

  @Test
  public void hasTargetCredential_IgnoresPrimaryAndIdpScopedProperties() {
    // A developer who only configured the primary client's or IdP client's credential must not
    // be told the target has one too.
    Properties properties = new Properties();
    properties.setProperty("clientSecret", "primary-secret");
    properties.setProperty("xaaIdpClientSecret", "idp-secret");

    assertThat(ClientAuthentication.hasTargetCredential(properties)).isFalse();
  }

  private static KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String toPem(PrivateKey privateKey) {
    String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
    StringBuilder pem = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
    for (int i = 0; i < base64.length(); i += 64) {
      pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
    }
    pem.append("-----END PRIVATE KEY-----\n");
    return pem.toString();
  }
}
