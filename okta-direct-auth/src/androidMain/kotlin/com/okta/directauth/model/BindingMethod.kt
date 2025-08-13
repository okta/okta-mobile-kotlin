package com.okta.directauth.model

internal enum class BindingMethod(val value: String) {
    NONE("none"),
    PROMPT("prompt"),
    TRANSFER("transfer"),
}
