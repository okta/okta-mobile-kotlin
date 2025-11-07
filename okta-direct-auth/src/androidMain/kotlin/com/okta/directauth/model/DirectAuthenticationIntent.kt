package com.okta.directauth.model

/**
 * Represents the intent of the direct authentication flow.
 *
 * This enum is used to specify whether the authentication request is for signing in or for account recovery.
 */
enum class DirectAuthenticationIntent(val value: String) {
    SIGN_IN("signIn"),
    RECOVERY("recovery"),
}
