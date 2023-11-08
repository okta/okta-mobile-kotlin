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

import com.okta.idx.android.infrastructure.EndToEndCredentials
import com.okta.idx.android.infrastructure.management.OktaManagementSdk
import com.okta.sdk.resource.api.UserApi
import com.okta.sdk.resource.model.User
import com.okta.sdk.resource.user.UserBuilder
import io.cucumber.java.After
import io.cucumber.java.Before
import org.junit.Assert
import timber.log.Timber

class RequireExistingUser {
    private val userApi = UserApi(OktaManagementSdk.client)

    @Before("@requireExistingUser", order = 100) fun createUserBeforeScenario() {
        Assert.assertNotNull(SharedState.a18NProfile)
        val user: User = UserBuilder.instance()
            .setEmail(SharedState.a18NProfile!!.emailAddress)
            .setFirstName(EndToEndCredentials["/cucumber/firstName"])
            .setLastName(SharedState.a18NProfile!!.profileId)
            .setPassword(EndToEndCredentials["/cucumber/password"].toCharArray())
            .setMobilePhone(SharedState.a18NProfile!!.phoneNumber)
            .setActive(true)
            .buildAndCreate(userApi)
        Assert.assertNotNull(user.id)
        Timber.i("User created: %s", user.profile?.email)
        SharedState.user = user
    }

    @After("@requireExistingUser") fun deleteUserAfterScenario() {
        val user = SharedState.user
        if (user != null) {
            val userEmail = user.profile?.email
            userApi.deactivateUser(user.id, false)
            userApi.deleteUser(user.id, false)
            SharedState.user = null
            Timber.i("User deleted: %s", userEmail)
        } else {
            Timber.w("No user to delete")
        }
    }
}
