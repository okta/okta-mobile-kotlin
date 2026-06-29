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
package com.okta.directauth.cli.viewmodel;

import com.okta.directauth.cli.model.DeviceCodeDisplay;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.TokenDisplay;

/** Callback interface for {@link OAuth2ViewModel} state changes. */
public interface OAuth2ViewModelListener {

  /**
   * Called when the current OAuth2 screen changes.
   *
   * @param screen the new screen
   */
  void onScreenChanged(OAuth2Screen screen);

  /**
   * Called when the device authorization code is ready for display.
   *
   * @param deviceCode the device code details to show the user
   */
  void onDeviceCode(DeviceCodeDisplay deviceCode);

  /**
   * Called when a flow completes successfully with tokens.
   *
   * @param token the token display object
   */
  void onResult(TokenDisplay token);

  /**
   * Called when a flow fails with a readable error message.
   *
   * @param message the error message
   */
  void onError(String message);
}
