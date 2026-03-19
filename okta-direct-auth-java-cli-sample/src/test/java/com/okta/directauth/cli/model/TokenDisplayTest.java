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
package com.okta.directauth.cli.model;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class TokenDisplayTest {

  @Test
  public void builder_CreatesImmutableObject() {
    TokenDisplay token =
        new TokenDisplay.Builder("access123", "Bearer", 3600)
            .scope("openid profile")
            .refreshToken("refresh456")
            .idToken("id789")
            .build();

    assertThat(token.getAccessToken()).isEqualTo("access123");
    assertThat(token.getTokenType()).isEqualTo("Bearer");
    assertThat(token.getExpiresIn()).isEqualTo(3600);
    assertThat(token.getScope()).isEqualTo("openid profile");
    assertThat(token.getRefreshToken()).isEqualTo("refresh456");
    assertThat(token.getIdToken()).isEqualTo("id789");
  }

  @Test
  public void builder_NullableFieldsDefaultToNull() {
    TokenDisplay token = new TokenDisplay.Builder("access123", "Bearer", 3600).build();

    assertThat(token.getAccessToken()).isEqualTo("access123");
    assertThat(token.getScope()).isNull();
    assertThat(token.getRefreshToken()).isNull();
    assertThat(token.getIdToken()).isNull();
  }

  @Test
  public void builder_RequiredFieldsPreserved() {
    TokenDisplay token = new TokenDisplay.Builder("token", "Bearer", 1800).build();

    assertThat(token.getAccessToken()).isEqualTo("token");
    assertThat(token.getTokenType()).isEqualTo("Bearer");
    assertThat(token.getExpiresIn()).isEqualTo(1800);
  }
}
