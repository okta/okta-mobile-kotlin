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
import com.okta.oauth2.kmp.SubjectAssertion;

/**
 * The three kinds of token this sample can present as a Cross App Access subject, mirroring {@link
 * SubjectAssertion.Type}.
 *
 * <p>{@link #IDENTITY} is the only kind universally accepted by an identity provider; {@link
 * #ACCESS} and {@link #REFRESH} are Okta deployment extensions that depend on the org's own
 * configuration. Matches the Kotlin {@code SubjectKind} in the shared module semantically, so the
 * Java CLI is not a weaker reference than the Compose sample.
 */
public enum SubjectKind {
  IDENTITY,
  ACCESS,
  REFRESH;

  /**
   * Whether {@code token} carries the value this kind needs.
   *
   * <p>{@link #ACCESS} is always available, since every {@link TokenInfo} carries an access token.
   * {@link #IDENTITY} and {@link #REFRESH} depend on whether the session that produced {@code
   * token} requested the corresponding token.
   *
   * @param token the session token to check
   * @return true if this kind's value is present in {@code token}
   */
  public boolean availableIn(TokenInfo token) {
    switch (this) {
      case IDENTITY:
        return token.getIdToken() != null && !token.getIdToken().isBlank();
      case ACCESS:
        return true;
      case REFRESH:
        return token.getRefreshToken() != null && !token.getRefreshToken().isBlank();
      default:
        throw new IllegalStateException("Unknown SubjectKind: " + this);
    }
  }

  /**
   * Builds a {@link SubjectAssertion} of this kind from {@code value}.
   *
   * <p>Only meaningful when the value actually corresponds to this kind — callers deriving from a
   * {@link TokenInfo} must check {@link #availableIn} first.
   *
   * @param value the token value to pair with this kind
   * @return a subject assertion pairing {@code value} with this kind
   */
  public SubjectAssertion toSubjectAssertion(String value) {
    switch (this) {
      case IDENTITY:
        return SubjectAssertion.idToken(value);
      case ACCESS:
        return SubjectAssertion.accessToken(value);
      case REFRESH:
        return SubjectAssertion.refreshToken(value);
      default:
        throw new IllegalStateException("Unknown SubjectKind: " + this);
    }
  }
}
