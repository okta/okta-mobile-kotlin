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

import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext;

/**
 * Immutable snapshot of a device authorization code for display purposes.
 *
 * <p>Use {@link #fromContext(DeviceAuthorizationFlowContext)} to construct from the SDK type.
 */
public final class DeviceCodeDisplay {
  private final String userCode;
  private final String verificationUri;
  private final String verificationUriComplete;
  private final int expiresIn;

  private DeviceCodeDisplay(
      String userCode, String verificationUri, String verificationUriComplete, int expiresIn) {
    this.userCode = userCode;
    this.verificationUri = verificationUri;
    this.verificationUriComplete = verificationUriComplete;
    this.expiresIn = expiresIn;
  }

  /**
   * Creates a {@link DeviceCodeDisplay} from the SDK {@link DeviceAuthorizationFlowContext}.
   *
   * @param context the SDK device authorization context
   * @return a new DeviceCodeDisplay instance
   */
  public static DeviceCodeDisplay fromContext(DeviceAuthorizationFlowContext context) {
    return new DeviceCodeDisplay(
        context.getUserCode(),
        context.getVerificationUri(),
        context.getVerificationUriComplete(),
        context.getExpiresIn());
  }

  /**
   * Returns the user code to enter at the verification URI.
   *
   * @return user code
   */
  public String getUserCode() {
    return userCode;
  }

  /**
   * Returns the verification URI the user should visit.
   *
   * @return verification URI
   */
  public String getVerificationUri() {
    return verificationUri;
  }

  /**
   * Returns the complete verification URI with the code pre-filled, or null if not provided.
   *
   * @return complete verification URI, or null
   */
  public String getVerificationUriComplete() {
    return verificationUriComplete;
  }

  /**
   * Returns the number of seconds until the device code expires.
   *
   * @return seconds until expiry
   */
  public int getExpiresIn() {
    return expiresIn;
  }
}
