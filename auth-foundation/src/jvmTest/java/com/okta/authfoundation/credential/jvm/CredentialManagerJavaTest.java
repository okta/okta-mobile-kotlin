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
package com.okta.authfoundation.credential.jvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.okta.authfoundation.client.jvm.AuthFoundationResult;
import com.okta.authfoundation.client.jvm.OAuth2ClientBuilder;
import com.okta.authfoundation.client.kmp.OAuth2Client;
import com.okta.authfoundation.credential.TokenMetadata;
import com.okta.authfoundation.credential.kmp.Credential;
import com.okta.authfoundation.credential.kmp.TokenData;
import com.okta.authfoundation.events.Event;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pure Java tests verifying that the CredentialManager API is usable from Java. */
public class CredentialManagerJavaTest {

  private static final String ISSUER_URL = "https://example.okta.com/oauth2/default";
  private static final String CLIENT_ID = "test-client-id";

  private OAuth2Client client;
  private CredentialManager manager;

  @Before
  public void setUp() {
    client =
        new OAuth2ClientBuilder(ISSUER_URL, CLIENT_ID, Arrays.asList("openid", "profile"))
            .build()
            .getOrThrow();
    manager = new CredentialManager(client);
  }

  @After
  public void tearDown() {
    manager.close();
  }

  @Test
  public void createTokenData_ReturnsTokenWithCorrectFields() {
    TokenData token = manager.createTokenData("my-access-token");

    assertNotNull("Token should not be null", token);
    assertEquals("my-access-token", token.getAccessToken());
    assertEquals("Bearer", token.getTokenType());
    assertEquals(3600, token.getExpiresIn());
  }

  @Test
  public void createTokenData_WithAllFields_ReturnsCorrectToken() {
    TokenData token = manager.createTokenData("at", "Bearer", 7200, "openid", "rt", "idt", "ds");

    assertEquals("at", token.getAccessToken());
    assertEquals("Bearer", token.getTokenType());
    assertEquals(7200, token.getExpiresIn());
    assertEquals("openid", token.getScope());
    assertEquals("rt", token.getRefreshToken());
    assertEquals("idt", token.getIdToken());
    assertEquals("ds", token.getDeviceSecret());
  }

  @Test
  public void store_AndGet_RoundTrips() {
    TokenData token = manager.createTokenData("store-at");

    AuthFoundationResult<Credential> storeResult = manager.store(token);

    assertTrue("Store should succeed", storeResult.isSuccess());

    Credential stored = storeResult.getOrThrow();
    assertNotNull("Stored credential should not be null", stored);
    assertEquals("store-at", stored.getToken().getAccessToken());

    // Retrieve by ID
    AuthFoundationResult<Credential> getResult = manager.get(stored.getId());
    assertTrue("Get should succeed", getResult.isSuccess());
    assertNotNull("Retrieved credential should not be null", getResult.getOrThrow());
    assertEquals(stored.getId(), getResult.getOrThrow().getId());
  }

  @Test
  public void store_WithTags_PreservesTags() {
    TokenData token = manager.createTokenData("tagged-at");
    Map<String, String> tags = Collections.singletonMap("env", "test");

    Credential credential = manager.store(token, tags).getOrThrow();

    assertEquals("test", credential.getTags().get("env"));
  }

  @Test
  public void allIds_ReturnsStoredIds() {
    TokenData t1 = manager.createTokenData("at-1");
    TokenData t2 = manager.createTokenData("at-2");
    manager.store(t1).getOrThrow();
    manager.store(t2).getOrThrow();

    List<String> ids = manager.allIds().getOrThrow();

    assertEquals(2, ids.size());
  }

  @Test
  public void metadata_ReturnsNullForNonexistent() {
    AuthFoundationResult<TokenMetadata> result = manager.metadata("nonexistent");
    assertTrue("Metadata lookup should succeed", result.isSuccess());
    assertNull("Metadata for nonexistent ID should be null", result.getOrNull());
  }

  @Test
  public void get_NonexistentId_ReturnsNull() {
    AuthFoundationResult<Credential> result = manager.get("no-such-id");
    assertTrue("Get should succeed", result.isSuccess());
    assertNull("Get for nonexistent ID should return null", result.getOrNull());
  }

  @Test
  public void setDefault_AndGetDefault_RoundTrips() {
    TokenData token = manager.createTokenData("default-at");
    Credential credential = manager.store(token).getOrThrow();

    manager.setDefault(credential).getOrThrow();

    Credential defaultCred = manager.getDefault().getOrThrow();
    assertNotNull("Default credential should not be null", defaultCred);
    assertEquals(credential.getId(), defaultCred.getId());
  }

  @Test
  public void setDefault_Null_ClearsDefault() {
    TokenData token = manager.createTokenData("clear-at");
    Credential credential = manager.store(token).getOrThrow();
    manager.setDefault(credential).getOrThrow();

    manager.setDefault(null).getOrThrow();

    assertNull("Default should be null after clearing", manager.getDefault().getOrNull());
  }

  @Test
  public void addEventListener_ReceivesEvents() throws Exception {
    List<Event> events = new ArrayList<>();
    Closeable registration = manager.addEventListener(events::add);

    // Allow the collector coroutine to start
    Thread.sleep(100);

    manager.store(manager.createTokenData("listener-at")).getOrThrow();

    // Allow event propagation
    Thread.sleep(100);

    assertTrue("Should have received at least one event", events.size() >= 1);
    registration.close();
  }

  @Test
  public void removeEventListener_StopsEvents() throws Exception {
    List<Event> events = new ArrayList<>();
    Closeable registration = manager.addEventListener(events::add);

    // Allow collector to start
    Thread.sleep(100);

    manager.store(manager.createTokenData("pre-remove-at")).getOrThrow();
    Thread.sleep(100);

    int countBefore = events.size();
    registration.close();

    manager.store(manager.createTokenData("post-remove-at")).getOrThrow();
    Thread.sleep(100);

    assertEquals("No new events after listener removal", countBefore, events.size());
  }

  @Test
  public void delete_RemovesCredential() {
    TokenData token = manager.createTokenData("delete-at");
    Credential credential = manager.store(token).getOrThrow();
    String id = credential.getId();

    manager.delete(id).getOrThrow();

    assertNull("Credential should be null after delete", manager.get(id).getOrNull());
  }
}
