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

import com.okta.directauth.cli.model.CliScreen;
import com.okta.directauth.jvm.DirectAuthenticationState;

/** Observer callback interface for ViewModel state changes. */
public interface AuthViewModelListener {

  /**
   * Called when the current screen changes.
   *
   * @param screen the new screen
   */
  void onScreenChanged(CliScreen screen);

  /**
   * Called when the authentication state changes.
   *
   * @param state the new authentication state
   */
  void onAuthStateChanged(DirectAuthenticationState state);

  /**
   * Called when an error occurs.
   *
   * @param message the error message
   */
  void onError(String message);
}
