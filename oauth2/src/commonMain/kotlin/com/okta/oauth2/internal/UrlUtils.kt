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
package com.okta.oauth2.internal

import io.ktor.http.Url
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parses a query parameter value from a URL string.
 *
 * @param url the full URL string containing query parameters.
 * @param key the parameter name to look up.
 * @return the decoded parameter value, or null if not found.
 */
internal fun parseQueryParameter(
    url: String,
    key: String,
): String? = Url(url).parameters[key]

/**
 * Generates a random UUID v4 string.
 *
 * @return a UUID string in standard 8-4-4-4-12 format.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun generateUuid(): String = Uuid.random().toString()
