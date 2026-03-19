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
package com.okta.directauth.jvm;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Pure Java tests verifying that the DirectAuthenticationFlowBuilder API is usable from Java. */
public class BuilderApiTest {

  private static final String ISSUER_URL = "https://example.okta.com";
  private static final String CLIENT_ID = "test_client_id";
  private static final List<String> SCOPE = Arrays.asList("openid", "profile", "offline_access");

  @Test
  public void build_WithRequiredParamsOnly_Succeeds() {
    DirectAuthenticationFlowBuilder builder =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE);

    DirectAuthResult<DirectAuthenticationFlow> result = builder.build();

    assertTrue("Build with required params should succeed", result.isSuccess());
    assertNotNull("Flow should not be null", result.getOrThrow());

    result.getOrThrow().close();
  }

  @Test
  public void build_WithChainingSetters_Succeeds() {
    Map<String, String> additionalParams = new HashMap<>();
    additionalParams.put("key", "value");

    DirectAuthenticationFlow flow =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE)
            .setAuthorizationServerId("default")
            .setClientSecret("secret")
            .setAcrValues(Collections.singletonList("urn:okta:loa:2fa:any"))
            .setAdditionalParameters(additionalParams)
            .build()
            .getOrThrow();

    assertNotNull("Flow should not be null after chaining", flow);
    flow.close();
  }

  @Test
  public void build_WithInvalidIssuerUrl_Fails() {
    DirectAuthenticationFlowBuilder builder =
        new DirectAuthenticationFlowBuilder("not-a-url", CLIENT_ID, SCOPE);

    DirectAuthResult<DirectAuthenticationFlow> result = builder.build();

    assertTrue("Build with invalid URL should fail", result.isFailure());
  }

  @Test
  public void build_WithEmptyClientId_Fails() {
    DirectAuthenticationFlowBuilder builder =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, "", SCOPE);

    DirectAuthResult<DirectAuthenticationFlow> result = builder.build();

    assertTrue("Build with empty clientId should fail", result.isFailure());
  }

  @Test
  public void build_WithEmptyScope_Fails() {
    DirectAuthenticationFlowBuilder builder =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, Collections.emptyList());

    DirectAuthResult<DirectAuthenticationFlow> result = builder.build();

    assertTrue("Build with empty scope should fail", result.isFailure());
  }
}
