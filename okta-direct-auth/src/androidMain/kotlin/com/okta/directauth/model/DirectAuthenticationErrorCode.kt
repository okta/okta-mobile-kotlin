package com.okta.directauth.model

/**
 * Error codes specific to Direct Authentication.
 */
internal enum class DirectAuthenticationErrorCode(val code: String) {
    MFA_REQUIRED("mfa_required"), // Custom error code for MFA requirement
    AUTHORIZATION_PENDING("authorization_pending"), // Custom error code for authorization pending

    // following are standard OAuth2 error codes https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1
    ACCESS_DENIED("access_denied"),
    INVALID_GRANT("invalid_grant"),
    INVALID_REQUEST("invalid_request"),
    INVALID_SCOPE("invalid_scope"),
    INVALID_CLIENT("invalid_client"),
    UNAUTHORIZED_CLIENT("unauthorized_client"),
    UNSUPPORTED_GRANT_TYPE("unsupported_grant_type"),
    UNSUPPORTED_RESPONSE_TYPE("unsupported_response_type"),
    TEMPORARILY_UNAVAILABLE("temporarily_unavailable"),
    SERVER_ERROR("server_error");

    companion object {
        fun fromString(value: String): DirectAuthenticationErrorCode = when (value) {
            MFA_REQUIRED.code -> MFA_REQUIRED
            AUTHORIZATION_PENDING.code -> AUTHORIZATION_PENDING
            ACCESS_DENIED.code -> ACCESS_DENIED
            INVALID_GRANT.code -> INVALID_GRANT
            INVALID_REQUEST.code -> INVALID_REQUEST
            INVALID_SCOPE.code -> INVALID_SCOPE
            INVALID_CLIENT.code -> INVALID_CLIENT
            UNAUTHORIZED_CLIENT.code -> UNAUTHORIZED_CLIENT
            UNSUPPORTED_GRANT_TYPE.code -> UNSUPPORTED_GRANT_TYPE
            UNSUPPORTED_RESPONSE_TYPE.code -> UNSUPPORTED_RESPONSE_TYPE
            TEMPORARILY_UNAVAILABLE.code -> TEMPORARILY_UNAVAILABLE
            SERVER_ERROR.code -> SERVER_ERROR
            else -> throw IllegalArgumentException("Unknown DirectAuthenticationErrorCode: $value")
        }
    }
}