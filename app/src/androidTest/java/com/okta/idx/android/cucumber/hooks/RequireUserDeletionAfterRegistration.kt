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
package com.okta.idx.android.cucumber.hooks

import com.okta.idx.android.infrastructure.management.OktaManagementSdk
import com.okta.sdk.resource.user.User
import io.cucumber.java.After
import timber.log.Timber
import java.util.Optional

class RequireUserDeletionAfterRegistration {
    @After("@requireUserDeletionAfterRegistration") fun deleteUserAfterRegistration() {
        val a18nProfile = SharedState.a18NProfile ?: return
        Timber.i("Searching for a user to be deleted: %s", a18nProfile.emailAddress)
        val userToDelete: Optional<User> =
            OktaManagementSdk.client.listUsers(a18nProfile.emailAddress, null, null, null, null)
                .stream().filter { x ->
                    x.profile.email.equals(a18nProfile.emailAddress)
                }.findFirst()
        if (userToDelete.isPresent) {
            val userEmail: String = userToDelete.get().profile.email
            userToDelete.get().deactivate()
            userToDelete.get().delete()
            Timber.i("User deleted: %s", userEmail)
        } else {
            Timber.w("Fail to find a user to delete: %s", a18nProfile.emailAddress)
        }
    }
}
