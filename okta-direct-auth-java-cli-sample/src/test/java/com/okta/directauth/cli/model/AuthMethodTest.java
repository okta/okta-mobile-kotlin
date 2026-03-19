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

import com.okta.directauth.model.OobChannel;
import com.okta.directauth.model.PrimaryFactor;
import org.junit.Test;

public class AuthMethodTest {

  @Test
  public void asFactor_Password_ReturnsPasswordFactor() {
    PrimaryFactor factor = AuthMethod.PASSWORD.asFactor("myPassword");
    assertThat(factor).isInstanceOf(PrimaryFactor.Password.class);
    assertThat(((PrimaryFactor.Password) factor).getPassword()).isEqualTo("myPassword");
  }

  @Test
  public void asFactor_Otp_ReturnsOtpFactor() {
    PrimaryFactor factor = AuthMethod.OTP.asFactor("123456");
    assertThat(factor).isInstanceOf(PrimaryFactor.Otp.class);
    assertThat(((PrimaryFactor.Otp) factor).getPassCode()).isEqualTo("123456");
  }

  @Test
  public void asFactor_Sms_ReturnsOobWithSmsChannel() {
    PrimaryFactor factor = AuthMethod.SMS.asFactor("");
    assertThat(factor).isInstanceOf(PrimaryFactor.Oob.class);
    assertThat(((PrimaryFactor.Oob) factor).getChannel()).isEqualTo(OobChannel.SMS);
  }

  @Test
  public void asFactor_Voice_ReturnsOobWithVoiceChannel() {
    PrimaryFactor factor = AuthMethod.VOICE.asFactor("");
    assertThat(factor).isInstanceOf(PrimaryFactor.Oob.class);
    assertThat(((PrimaryFactor.Oob) factor).getChannel()).isEqualTo(OobChannel.VOICE);
  }

  @Test
  public void asFactor_OktaVerify_ReturnsOobWithPushChannel() {
    PrimaryFactor factor = AuthMethod.OKTA_VERIFY.asFactor("");
    assertThat(factor).isInstanceOf(PrimaryFactor.Oob.class);
    assertThat(((PrimaryFactor.Oob) factor).getChannel()).isEqualTo(OobChannel.PUSH);
  }

  @Test
  public void getLabel_ReturnsDisplayName() {
    assertThat(AuthMethod.PASSWORD.getLabel()).isEqualTo("Password");
    assertThat(AuthMethod.OTP.getLabel()).isEqualTo("OTP");
    assertThat(AuthMethod.SMS.getLabel()).isEqualTo("SMS");
    assertThat(AuthMethod.VOICE.getLabel()).isEqualTo("Voice");
    assertThat(AuthMethod.OKTA_VERIFY.getLabel()).isEqualTo("Push (Okta Verify)");
  }
}
