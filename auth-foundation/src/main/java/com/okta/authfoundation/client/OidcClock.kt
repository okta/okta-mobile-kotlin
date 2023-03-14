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
package com.okta.authfoundation.client

import com.okta.authfoundation.AuthFoundationDefaults

/**
 * Interface used to return the current time from a trusted source.
 *
 * This can be used to customize the behavior of how the current time is calculated, when used on devices that may have skewed or incorrect clocks.
 *
 * To use a custom [OidcClock], you construct an instance of your class implementing this interface, and assign it to the [AuthFoundationDefaults.clock] property.
 */
fun interface OidcClock {

    /**
     *  Returns the current time in seconds since January 1, 1970 UTC, adjusting the system clock to correct for clock skew.
     */
    fun currentTimeEpochSecond(): Long
}
