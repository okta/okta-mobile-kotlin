package com.okta.idx.android.infrastructure.a18n

import com.fasterxml.jackson.annotation.JsonProperty

data class A18NProfile(
    @JsonProperty("profileId")
    val profileId: String,
    @JsonProperty("phoneNumber")
    val phoneNumber: String,
    @JsonProperty("emailAddress")
    val emailAddress: String,
    @JsonProperty("displayName")
    val displayName: String,
    @JsonProperty("url")
    val url: String
)