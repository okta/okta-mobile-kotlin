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

import com.okta.directauth.model.OobChannel;
import com.okta.directauth.model.PrimaryFactor;

/**
 * Supported authentication methods for the CLI sample app.
 *
 * <p>Each method maps to a display label for CLI menus and can produce the corresponding SDK {@link
 * PrimaryFactor} for authentication requests.
 */
public enum AuthMethod {
  PASSWORD("Password"),
  OTP("OTP"),
  SMS("SMS"),
  VOICE("Voice"),
  OKTA_VERIFY("Push (Okta Verify)");

  private final String label;

  AuthMethod(String label) {
    this.label = label;
  }

  /**
   * Returns the display label for CLI menus.
   *
   * @return the human-readable label
   */
  public String getLabel() {
    return label;
  }

  /**
   * Converts this authentication method to the corresponding SDK {@link PrimaryFactor}.
   *
   * @param passCode the passcode or password entered by the user (used by PASSWORD and OTP)
   * @return the SDK factor for this authentication method
   */
  public PrimaryFactor asFactor(String passCode) {
    if (this == PASSWORD) {
      return new PrimaryFactor.Password(passCode);
    } else if (this == OTP) {
      return new PrimaryFactor.Otp(passCode);
    } else if (this == SMS) {
      return new PrimaryFactor.Oob(OobChannel.SMS);
    } else if (this == VOICE) {
      return new PrimaryFactor.Oob(OobChannel.VOICE);
    } else if (this == OKTA_VERIFY) {
      return new PrimaryFactor.Oob(OobChannel.PUSH);
    }
    throw new IllegalStateException("Unknown auth method: " + this);
  }
}
