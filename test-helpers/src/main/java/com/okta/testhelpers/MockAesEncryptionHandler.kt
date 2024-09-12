/*
 * Copyright 2024-Present Okta, Inc.
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
package com.okta.testhelpers

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.util.AesEncryptionHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

@InternalAuthFoundationApi
object MockAesEncryptionHandler {
    fun getInstance() = mockk<AesEncryptionHandler>().apply {
        every { encryptString(any()) } returnsArgument 0
        every { decryptString(any()) } returnsArgument 0
        every { resetEncryptionKey() } just runs
    }
}
