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

import com.okta.authfoundation.client.jvm.OAuth2ClientBuilder;
import com.okta.authfoundation.client.kmp.OAuth2Client;
import java.util.Collections;

/**
 * Test helper providing a minimal {@link OAuth2Client} instance for Java tests.
 *
 * <p>Construction uses valid parameters (HTTPS issuer, non-blank clientId, non-empty scope) that
 * satisfy {@link OAuth2ClientBuilder} validation and succeed without network calls (endpoint
 * discovery is lazy).
 */
class TestOAuth2Client {

  private TestOAuth2Client() {}

  static OAuth2Client create() {
    return new OAuth2ClientBuilder(
            "https://example.okta.com", "test-client", Collections.singletonList("openid"))
        .build()
        .getOrThrow();
  }
}
