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
package com.okta.idx.android.sdk

import com.okta.idx.android.sdk.steps.ChallengeAuthenticatorStep
import com.okta.idx.android.sdk.steps.ChallengeAuthenticatorViewFactory
import com.okta.idx.android.sdk.steps.EnrollAuthenticatorStep
import com.okta.idx.android.sdk.steps.EnrollAuthenticatorViewFactory
import com.okta.idx.android.sdk.steps.IdentifyUsernameAndPasswordStep
import com.okta.idx.android.sdk.steps.IdentifyUsernameAndPasswordViewFactory
import com.okta.idx.android.sdk.steps.IdentifyUsernameStep
import com.okta.idx.android.sdk.steps.IdentifyUsernameViewFactory
import com.okta.idx.android.sdk.steps.SelectAuthenticatorStep
import com.okta.idx.android.sdk.steps.SelectAuthenticatorViewFactory
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.response.IDXResponse

object IdxViewRegistry {
    private class FactoryWrapper<ViewModel>(
        val stepFactory: StepFactory<ViewModel>,
        val viewFactory: ViewFactory<ViewModel>,
    ) {
        fun displayableStep(remediationOption: RemediationOption): DisplayableStep<ViewModel>? {
            val step = stepFactory.get(remediationOption)
            if (step != null) {
                return DisplayableStep(viewFactory, step)
            }
            return null
        }
    }

    private val stepHandlers = mutableMapOf<Class<*>, FactoryWrapper<*>>()

    init {
        register(ChallengeAuthenticatorStep.Factory(), ChallengeAuthenticatorViewFactory())
        register(
            IdentifyUsernameAndPasswordStep.Factory(),
            IdentifyUsernameAndPasswordViewFactory()
        )
        register(IdentifyUsernameStep.Factory(), IdentifyUsernameViewFactory())
        register(SelectAuthenticatorStep.Factory(), SelectAuthenticatorViewFactory())
        register(EnrollAuthenticatorStep.Factory(), EnrollAuthenticatorViewFactory())
    }

    fun <ViewModel> register(
        stepFactory: StepFactory<ViewModel>,
        viewFactory: ViewFactory<ViewModel>,
    ) {
        stepHandlers[stepFactory.javaClass] = FactoryWrapper(stepFactory, viewFactory)
    }

    fun asDisplaySteps(idxResponse: IDXResponse): List<DisplayableStep<*>> {
        val result = mutableListOf<DisplayableStep<*>>()
        val remediations = idxResponse.remediation()?.remediationOptions()

        if (remediations == null || remediations.isEmpty()) {
            throw IllegalStateException("Response not handled.")
        }

        for (remediation in remediations) {
            var handled = false

            for (stepHandler in stepHandlers.values) {
                val displayWrapper = stepHandler.displayableStep(remediation)
                if (displayWrapper != null) {
                    result += displayWrapper
                    handled = true
                    break
                }
            }

            if (!handled) {
                val message = "remediationOption not handled. ${remediation.name}"
                throw IllegalStateException(message)
            }
        }

        return result
    }
}
