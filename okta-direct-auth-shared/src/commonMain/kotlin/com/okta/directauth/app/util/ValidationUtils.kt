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
package com.okta.directauth.app.util

/**
 * Standard error message for blank fields.
 */
const val BLANK_FIELD_ERROR = "This field cannot be left blank"

/**
 * Carries the error handler for field validation so it need not be repeated at every
 * [validateNotBlank] call site inside the same composable scope.
 */
fun interface ValidationErrorContext {
    fun onValidationError()
}

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
    onSuccess: (String) -> Unit,
) {
    if (this.isBlank()) onError() else onSuccess(this)
}

/**
 * Context-parameterized variant of [validateNotBlank]. Calls [ValidationErrorContext.onValidationError]
 * on failure so callers need only supply [onSuccess].
 *
 * Usage example:
 * ```
 * context(ValidationErrorContext { showError = true }) {
 *     password.validateNotBlank { jobState.addJob(next(it)) }
 *     // keyboard action also gets the same error handler implicitly
 *     code.validateNotBlank { jobState.addJob(submit(it)) }
 * }
 * ```
 */
context(ctx: ValidationErrorContext)
inline fun String.validateNotBlank(onSuccess: (String) -> Unit) {
    if (isBlank()) ctx.onValidationError() else onSuccess(this)
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
    } else {
        true
    }
