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

/** Navigation states for the OAuth2 demonstration mode. */
public enum OAuth2Screen {
  MENU,
  INPUT,
  DEVICE_POLLING,
  BROWSER_WAITING,
  AUTHENTICATED,
  ERROR,

  /** Cross App Access: choosing a subject kind, ready to start either mode. */
  CROSS_APP_ACCESS_MENU,
  /** Cross App Access: the dedicated sign-in's browser flow is in flight. */
  CROSS_APP_ACCESS_SIGN_IN_WAITING,
  /** Cross App Access: the dedicated sign-in succeeded — a session is now available. */
  CROSS_APP_ACCESS_SIGNED_IN,
  /** Cross App Access: a step is in flight. */
  CROSS_APP_ACCESS_WORKING,
  /** Cross App Access: the ID-JAG was obtained (step-by-step mode only). */
  CROSS_APP_ACCESS_ID_JAG,
  /** Cross App Access: a resource access token was obtained. */
  CROSS_APP_ACCESS_RESULT,
  /** Cross App Access: the exchange failed. */
  CROSS_APP_ACCESS_ERROR
}
