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
import com.okta.sdk.resource.group.Group
import io.cucumber.core.api.Scenario
import io.cucumber.java.Before
import org.junit.Assert
import java.util.function.Consumer
import java.util.stream.Collectors

class RequireMFAGroupsForUser {
    @Before("@requireMFAGroupsForUser") fun assignMFAGroupBeforeScenario(scenario: Scenario) {
        Assert.assertNotNull(SharedState.user)
        val groups: MutableList<String> = ArrayList()
        groups.add("MFA Required")
        if (scenario.id.contains("mfa_with_password_and_sms")) {
            groups.add("Phone Enrollment Required")
        }
        val groupList: List<Group> = OktaManagementSdk.client.listGroups()
            .stream()
            .filter { group -> groups.contains(group.profile.name) }
            .collect(Collectors.toList())
        Assert.assertFalse(groupList.isEmpty())
        groupList.forEach(Consumer { group: Group ->
            SharedState.user!!.addToGroup(group.id)
        })
    }
}
