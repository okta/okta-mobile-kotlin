package com.okta.directauth.model

internal enum class BindingMethod(val value: String) {
    NONE("none"),
    PROMPT("prompt"),
    TRANSFER("transfer");

    companion object {
        fun fromString(bindingMethod: String): BindingMethod = when (bindingMethod) {
            NONE.value -> NONE
            PROMPT.value -> PROMPT
            TRANSFER.value -> TRANSFER
            else -> throw IllegalArgumentException("Unknown binding method: $bindingMethod")
        }
    }
}
