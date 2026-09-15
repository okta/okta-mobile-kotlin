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
package com.okta.directauth.app.ui

/**
 * The single source of every Cross App Access user-facing string in this sample.
 *
 * "Cross App Access" and "ID-JAG" are this capability's real names — the same names used by the
 * SDK's own KDoc, Okta's Cross App Access documentation, and the governing IETF draft
 * (draft-ietf-oauth-identity-assertion-authz-grant). Using them here, rather than an invented
 * plainer synonym, is what lets a developer connect what they see on screen to those other
 * documents. [ID_JAG_FIRST_USE] carries a one-time plain-language gloss for a screen's first
 * mention of the term; every later mention on that same screen should use [ID_JAG_TERM] alone.
 */
object XaaStrings {
    const val TITLE = "Cross App Access"
    const val MENU_LABEL = "Cross App Access"
    const val MENU_DESCRIPTION = "Exchange this session for a token at another app"

    const val SCREEN_DESCRIPTION =
        "Cross App Access lets this session be used to obtain a scoped access token for another " +
            "app (the resource app) in a different security domain — no second consent prompt, no " +
            "static API key."

    const val ID_JAG_TERM = "ID-JAG"
    const val ID_JAG_FIRST_USE =
        "ID-JAG (the short-lived assertion the identity provider issues, which is redeemed at the " +
            "resource app for a resource access token)"

    const val TARGET_LABEL = "Resource App Target"
    const val IDP_CLIENT_ID_LABEL = "IdP Client ID"
    const val IDP_ISSUER_LABEL = "IdP Issuer"

    const val SUBJECT_LABEL = "Subject"
    const val SUBJECT_KIND_NOTE =
        "Only the identity token is universally accepted; access and refresh tokens depend on " +
            "your org's own configuration."
    const val SUBJECT_KIND_UNAVAILABLE_SUFFIX = " (unavailable — not present in this session)"

    const val VIEW_TOKEN_BUTTON = "View"
    const val TOKEN_DIALOG_CLOSE_BUTTON = "Close"
    const val TOKEN_DIALOG_TITLE_SUFFIX = "Token"
    const val TOKEN_DIALOG_CLAIMS_HEADER = "=== Claims ==="
    const val TOKEN_DIALOG_NOT_A_JWT_NOTE = "Not a JWT (opaque token):"
    const val TOKEN_DIALOG_PARSE_FAILURE_PREFIX = "Failed to parse token:"

    const val SCOPE_LABEL = "Requested Scopes"
    const val SCOPE_PLACEHOLDER = "e.g. chat.read"

    const val SIGN_IN_BUTTON = "Sign in for Cross App Access"
    const val SIGNING_IN_NOTE = "Waiting for browser sign-in..."
    const val NO_SESSION_NOTE =
        "Cross App Access needs its own signed-in session, separate from the rest of this app — " +
            "sign in below."

    const val ONE_ACTION_BUTTON = "Exchange for Resource Access Token"
    const val STEP_ONE_BUTTON = "Obtain $ID_JAG_TERM"
    const val REDEEM_BUTTON = "Redeem for Resource Access Token"
    const val REDEEM_AGAIN_BUTTON = "Redeem Again"
    const val RESTART_AFTER_EXPIRY_BUTTON = "$ID_JAG_TERM Expired — Start Over"
    const val START_OVER_BUTTON = "Start Over"

    const val ID_JAG_SECTION_TITLE = "$ID_JAG_FIRST_USE Obtained"
    const val ID_JAG_AUDIENCE_LABEL = "Audience"
    const val ID_JAG_LIFETIME_LABEL = "Expires In"
    const val ID_JAG_SCOPE_LABEL = "Granted Scope"
    const val ID_JAG_REDEMPTION_COUNT_LABEL = "Resource Tokens Issued From This $ID_JAG_TERM"
    const val ID_JAG_REUSE_NOTE =
        "This $ID_JAG_TERM can be redeemed again for a fresh resource access token without " +
            "another round trip to the identity provider."
    const val ID_JAG_EXPIRED_NOTE = "This $ID_JAG_TERM has expired. Obtain a new one to continue."

    const val RESULT_SECTION_TITLE = "Resource Access Token"
    const val RESULT_FOR_RESOURCE_APP_NOTE = "This token is for the resource app — not your signed-in app."
    const val RESULT_SCOPE_LABEL = "Granted Scope"
    const val RESULT_TOKEN_TYPE_LABEL = "Token Type"
    const val RESULT_EXPIRY_LABEL = "Expires In"

    const val INTROSPECT_BUTTON = "Introspect This Token"
    const val INTROSPECTING_NOTE = "Checking with the resource authorization server..."
    const val INTROSPECT_ACTIVE_LABEL = "Active"

    const val NOT_CONFIGURED_TITLE = "Cross App Access Is Not Configured"
    const val NOT_CONFIGURED_HINT =
        "Add a resource app target to local.properties to try this out — see " +
            "okta-direct-auth-shared/README.md."

    const val FAILURE_TITLE = "Cross App Access Failed"
    const val TRY_AGAIN_BUTTON = "Try Again"
    const val BACK_TO_HOME_BUTTON = "Back to Home"

    /** Renders `count` seconds as a short human-readable duration for [ID_JAG_LIFETIME_LABEL]. */
    fun formatSeconds(seconds: Int): String = "${seconds}s"
}
