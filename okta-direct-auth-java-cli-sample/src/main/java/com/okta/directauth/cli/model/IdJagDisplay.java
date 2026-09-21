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

import com.okta.oauth2.kmp.IdJagAssertion;

/**
 * Immutable, metadata-only value object for displaying an {@link IdJagAssertion}.
 *
 * <p>Deliberately omits the assertion's full value — only a short, non-reversible preview is
 * carried — so no rendering path built from this type can ever print the complete ID-JAG.
 *
 * <p>Use {@link Builder} to construct instances.
 */
public final class IdJagDisplay {
  private static final int VALUE_PREVIEW_LENGTH = 12;

  private final String audience;
  private final int expiresIn;
  private final long issuedAt;
  private final String scope;
  private final String issuedTokenType;
  private final String valuePreview;
  private final int redemptionCount;

  private IdJagDisplay(Builder builder) {
    this.audience = builder.audience;
    this.expiresIn = builder.expiresIn;
    this.issuedAt = builder.issuedAt;
    this.scope = builder.scope;
    this.issuedTokenType = builder.issuedTokenType;
    this.valuePreview = builder.valuePreview;
    this.redemptionCount = builder.redemptionCount;
  }

  /**
   * Creates an {@link IdJagDisplay} from a SDK {@link IdJagAssertion}, given how many times it has
   * already been redeemed.
   *
   * @param idJag the SDK ID-JAG assertion
   * @param redemptionCount how many resource tokens this assertion has produced so far
   * @return a new IdJagDisplay instance
   */
  public static IdJagDisplay fromIdJagAssertion(IdJagAssertion idJag, int redemptionCount) {
    return new Builder(idJag.getAudience(), idJag.getExpiresIn(), idJag.getIssuedAt())
        .scope(idJag.getScope())
        .issuedTokenType(idJag.getIssuedTokenType())
        .valuePreview(previewOf(idJag.getValue()))
        .redemptionCount(redemptionCount)
        .build();
  }

  private static String previewOf(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    int length = Math.min(VALUE_PREVIEW_LENGTH, value.length());
    return value.substring(0, length) + "...";
  }

  public String getAudience() {
    return audience;
  }

  public int getExpiresIn() {
    return expiresIn;
  }

  public long getIssuedAt() {
    return issuedAt;
  }

  public String getScope() {
    return scope;
  }

  public String getIssuedTokenType() {
    return issuedTokenType;
  }

  /** A short, truncated preview of the assertion value — never the complete value. */
  public String getValuePreview() {
    return valuePreview;
  }

  public int getRedemptionCount() {
    return redemptionCount;
  }

  /** Builder for {@link IdJagDisplay}. */
  public static final class Builder {
    private final String audience;
    private final int expiresIn;
    private final long issuedAt;
    private String scope;
    private String issuedTokenType;
    private String valuePreview = "";
    private int redemptionCount;

    /**
     * Creates a new builder with required fields.
     *
     * @param audience the target authorization server this assertion was minted for
     * @param expiresIn the assertion's lifetime in seconds
     * @param issuedAt epoch seconds at which this assertion was received or restored
     */
    public Builder(String audience, int expiresIn, long issuedAt) {
      this.audience = audience;
      this.expiresIn = expiresIn;
      this.issuedAt = issuedAt;
    }

    public Builder scope(String scope) {
      this.scope = scope;
      return this;
    }

    public Builder issuedTokenType(String issuedTokenType) {
      this.issuedTokenType = issuedTokenType;
      return this;
    }

    public Builder valuePreview(String valuePreview) {
      this.valuePreview = valuePreview;
      return this;
    }

    public Builder redemptionCount(int redemptionCount) {
      this.redemptionCount = redemptionCount;
      return this;
    }

    public IdJagDisplay build() {
      return new IdJagDisplay(this);
    }
  }
}
