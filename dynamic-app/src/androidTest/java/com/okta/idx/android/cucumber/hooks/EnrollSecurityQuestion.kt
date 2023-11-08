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
import com.okta.sdk.resource.api.UserFactorApi
import com.okta.sdk.resource.model.FactorType
import com.okta.sdk.resource.model.SecurityQuestionUserFactor
import com.okta.sdk.resource.model.SecurityQuestionUserFactorProfile
import io.cucumber.java.Before
import org.junit.Assert
import java.util.UUID

class EnrollSecurityQuestion {
    companion object {
        val ANSWER = UUID.randomUUID().toString()
    }

    @Before("@enrollSecurityQuestion") fun requireEnrolledSecurityQuestion() {
        Assert.assertNotNull(SharedState.user)
        val userFactorApi = UserFactorApi(OktaManagementSdk.client)
        val factor = SecurityQuestionUserFactor().apply {
            setProfile(
                SecurityQuestionUserFactorProfile()
                    .question("disliked_food")
                    .questionText("What is the food you least liked as a child?")
                    .answer(ANSWER)
            )
            factorType(FactorType.QUESTION)
        }
        userFactorApi.enrollFactor(SharedState.user!!.id, factor, false, null, null, true)
    }
}
