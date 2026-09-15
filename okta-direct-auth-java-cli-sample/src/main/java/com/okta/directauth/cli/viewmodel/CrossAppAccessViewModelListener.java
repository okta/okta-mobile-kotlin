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

import com.okta.directauth.cli.model.IdJagDisplay;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.TokenDisplay;

/** Callback interface for {@link CrossAppAccessViewModel} state changes. */
public interface CrossAppAccessViewModelListener {

  /**
   * Called when the current Cross App Access screen changes.
   *
   * @param screen the new screen
   */
  void onScreenChanged(OAuth2Screen screen);

  /**
   * Called when the first step succeeds and an ID-JAG is available (step-by-step mode).
   *
   * @param idJag the ID-JAG display object
   */
  void onIdJag(IdJagDisplay idJag);

  /**
   * Called when a resource access token is obtained, from either mode.
   *
   * @param token the resource access token display object
   */
  void onResourceToken(TokenDisplay token);

  /**
   * Called when introspecting the current resource access token completes — a verdict on the
   * existing token from {@link #onResourceToken}, not a new one.
   *
   * @param active whether the resource authorization server reports the token as active
   */
  void onIntrospection(Boolean active);

  /**
   * Called when the exchange fails with a readable, server-attributed error message.
   *
   * @param message the error message
   */
  void onError(String message);
}
