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
import com.okta.sdk.resource.api.GroupApi
import com.okta.sdk.resource.model.Group
import io.cucumber.java.Before
import org.junit.Assert

class RequireGroupsForUser {
    @Before("@requireMFAGroupsForUser") fun assignMFAGroupBeforeScenario() {
        assignGroupForUser("MFA Required")
    }

    @Before("@requirePhoneGroupForUser") fun assignPhoneGroupBeforeScenario() {
        assignGroupForUser("Phone Enrollment Required")
    }

    @Before("@requireSecurityQuestionGroupForUser") fun assignSecurityQuestionGroup() {
        assignGroupForUser("RequireSecurityQuestion")
    }

    private fun assignGroupForUser(groupName: String) {
        Assert.assertNotNull(SharedState.user)
        val groupApi = GroupApi(OktaManagementSdk.client)
        val groupList = groupApi.listGroups(null, null, null, 20, null, null, null, null)
            .filter { it.profile?.name == groupName }
        Assert.assertFalse(groupList.isEmpty())
        groupList.forEach { group: Group ->
            groupApi.assignUserToGroup(group.id, SharedState.user!!.id)
        }
    }
}
