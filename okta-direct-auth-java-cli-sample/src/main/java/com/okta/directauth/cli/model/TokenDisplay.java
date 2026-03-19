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

import com.okta.authfoundation.client.TokenInfo;

/**
 * Immutable value object representing token information for display purposes.
 *
 * <p>Use {@link Builder} to construct instances.
 */
public final class TokenDisplay {
  private final String accessToken;
  private final String tokenType;
  private final int expiresIn;
  private final String scope;
  private final String refreshToken;
  private final String idToken;

  private TokenDisplay(Builder builder) {
    this.accessToken = builder.accessToken;
    this.tokenType = builder.tokenType;
    this.expiresIn = builder.expiresIn;
    this.scope = builder.scope;
    this.refreshToken = builder.refreshToken;
    this.idToken = builder.idToken;
  }

  /**
   * Creates a {@link TokenDisplay} from a SDK {@link TokenInfo}.
   *
   * @param tokenInfo the SDK token info
   * @return a new TokenDisplay instance
   */
  public static TokenDisplay fromTokenInfo(TokenInfo tokenInfo) {
    return new Builder(
            tokenInfo.getAccessToken(), tokenInfo.getTokenType(), tokenInfo.getExpiresIn())
        .scope(tokenInfo.getScope())
        .refreshToken(tokenInfo.getRefreshToken())
        .idToken(tokenInfo.getIdToken())
        .build();
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  public int getExpiresIn() {
    return expiresIn;
  }

  public String getScope() {
    return scope;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public String getIdToken() {
    return idToken;
  }

  /** Builder for {@link TokenDisplay}. */
  public static final class Builder {
    private final String accessToken;
    private final String tokenType;
    private final int expiresIn;
    private String scope;
    private String refreshToken;
    private String idToken;

    /**
     * Creates a new builder with required fields.
     *
     * @param accessToken the access token string
     * @param tokenType the token type (e.g. "Bearer")
     * @param expiresIn the expiration duration in seconds
     */
    public Builder(String accessToken, String tokenType, int expiresIn) {
      this.accessToken = accessToken;
      this.tokenType = tokenType;
      this.expiresIn = expiresIn;
    }

    public Builder scope(String scope) {
      this.scope = scope;
      return this;
    }

    public Builder refreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
      return this;
    }

    public Builder idToken(String idToken) {
      this.idToken = idToken;
      return this;
    }

    public TokenDisplay build() {
      return new TokenDisplay(this);
    }
  }
}
