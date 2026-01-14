package com.okta.directauth.app.util

/**
 * Validation utilities for common input validation patterns.
 */

/**
 * Standard error message for blank fields.
 */
const val BLANK_FIELD_ERROR = "This field cannot be left blank"

/**
 * Validates that a string is not blank.
 * Calls [onError] if validation fails, or [onSuccess] with the string if validation passes.
 *
 * Usage example:
 * ```
 * password.validateNotBlank(
 *     onError = { showError = true },
 *     onSuccess = { validPassword ->
 *         jobState.execute(next(validPassword))
 *     }
 * )
 * ```
 */
inline fun String.validateNotBlank(
    onError: () -> Unit,
    onSuccess: (String) -> Unit
) {
    if (this.isBlank()) onError() else onSuccess(this)
}

/**
 * Validates that a string is not blank, returning a boolean result.
 * Sets [errorState] to true if validation fails.
 *
 * Usage example:
 * ```
 * if (password.isNotBlankOrError { showError = it }) {
 *     // proceed with valid password
 * }
 * ```
 */
inline fun String.isNotBlankOrError(errorState: (Boolean) -> Unit): Boolean =
    if (this.isBlank()) {
        errorState(true)
        false
    } else true
