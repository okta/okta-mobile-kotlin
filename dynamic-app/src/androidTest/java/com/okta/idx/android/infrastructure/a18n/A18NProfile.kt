/*
 * Copyright 2021-Present Okta, Inc.
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
