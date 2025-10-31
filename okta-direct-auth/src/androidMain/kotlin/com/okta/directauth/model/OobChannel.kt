package com.okta.directauth.model

/**
 * Represents the channel for an Out-of-Band (OOB) authentication challenge.
 *
 * OOB challenges are used in multi-factor authentication to send a code or a prompt
 * to a user through a separate communication channel, such as SMS or email, to verify
 * their identity.
 */
enum class OobChannel(val value: String) {
    /**
     * Specifies that the OOB challenge should be sent as a push notification
     * to the user's registered device (e.g., via an authenticator app).
     */
    PUSH("push"),

    /**
     * Specifies that the OOB challenge should be sent via SMS to the user's
     * registered phone number.
     */
    SMS("sms"),

    /**
     * Specifies that the OOB challenge should be delivered via a voice call
     * to the user's registered phone number.
     */
    VOICE("voice");

    internal companion object {
        fun fromString(oobChannel: String): OobChannel = when (oobChannel) {
            PUSH.value -> PUSH
            SMS.value -> SMS
            VOICE.value -> VOICE
            else -> throw IllegalArgumentException("Unknown OOB channel: $oobChannel")
        }
    }
}