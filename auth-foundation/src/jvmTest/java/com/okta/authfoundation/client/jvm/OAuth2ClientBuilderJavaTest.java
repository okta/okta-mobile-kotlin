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
package com.okta.authfoundation.client.jvm;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.okta.authfoundation.client.kmp.OAuth2Client;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Pure Java tests verifying that the JvmOAuth2ClientBuilder API is usable from Java. */
public class OAuth2ClientBuilderJavaTest {

  private static final String ISSUER_URL = "https://example.okta.com/oauth2/default";
  private static final String CLIENT_ID = "test-client-id";
  private static final String SCOPE = "openid profile";

  @Test
  public void build_WithRequiredParamsOnly_Succeeds() {
    OAuth2ClientBuilder builder =
        new OAuth2ClientBuilder(ISSUER_URL, CLIENT_ID, Arrays.asList(SCOPE.split(" ")));

    AuthFoundationResult<OAuth2Client> result = builder.build();

    assertTrue("Build with required params should succeed", result.isSuccess());
    assertNotNull("Client should not be null", result.getOrThrow());
  }

  @Test
  public void build_WithChainingSetters_Succeeds() {
    OAuth2Client client =
        new OAuth2ClientBuilder(ISSUER_URL, CLIENT_ID, Arrays.asList(SCOPE.split(" ")))
            .setAuthorizationServerId("default")
            .setClientSecret("secret")
            .setAcrValues("urn:okta:loa:2fa:any")
            .build()
            .getOrThrow();

    assertNotNull("Client should not be null after chaining", client);
  }

  @Test
  public void build_WithInvalidIssuerUrl_Fails() {
    OAuth2ClientBuilder builder =
        new OAuth2ClientBuilder(
            "http://insecure.example.com", CLIENT_ID, Arrays.asList(SCOPE.split(" ")));

    AuthFoundationResult<OAuth2Client> result = builder.build();

    assertTrue("Build with HTTP URL should fail", result.isFailure());
  }

  @Test
  public void build_WithEmptyClientId_Fails() {
    OAuth2ClientBuilder builder =
        new OAuth2ClientBuilder(ISSUER_URL, "", Arrays.asList(SCOPE.split(" ")));

    AuthFoundationResult<OAuth2Client> result = builder.build();

    assertTrue("Build with empty clientId should fail", result.isFailure());
  }

  @Test
  public void build_WithEmptyScope_Fails() {
    OAuth2ClientBuilder builder =
        new OAuth2ClientBuilder(ISSUER_URL, CLIENT_ID, Collections.emptyList());

    AuthFoundationResult<OAuth2Client> result = builder.build();

    assertTrue("Build with empty scope should fail", result.isFailure());
  }
}
