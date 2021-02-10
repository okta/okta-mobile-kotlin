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
package com.okta.idx.android.sdk.steps

import android.view.View
import com.okta.idx.android.databinding.StepButtonBinding
import com.okta.idx.android.sdk.Step
import com.okta.idx.android.sdk.StepFactory
import com.okta.idx.android.sdk.StepState
import com.okta.idx.android.sdk.ViewFactory
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.response.IDXResponse

class ButtonStep private constructor(
    val viewModel: ViewModel,
) : Step {
    class Factory : StepFactory<ButtonStep> {
        override fun get(remediationOption: RemediationOption): ButtonStep? {
            return when (remediationOption.name) {
                "skip" -> {
                    ButtonStep(
                        ViewModel(
                            remediationOption = remediationOption,
                            action = Action.Skip,
                            name = "Skip"
                        )
                    )
                }
                "select-identify" -> {
                    ButtonStep(
                        ViewModel(
                            remediationOption = remediationOption,
                            action = Action.SelectIdentify,
                            name = "Select Identify"
                        )
                    )
                }
                "select-enroll-profile" -> {
                    ButtonStep(
                        ViewModel(
                            remediationOption = remediationOption,
                            action = Action.SelectEnrollProfile,
                            name = "Select Enroll Profile"
                        )
                    )
                }
                else -> {
                    null
                }
            }
        }
    }

    sealed class Action {
        object Skip : Action() {
            override fun proceed(
                state: StepState,
                remediationOption: RemediationOption
            ): IDXResponse {
                return state.skip(remediationOption)
            }
        }

        object SelectIdentify : Action() {
            override fun proceed(
                state: StepState,
                remediationOption: RemediationOption
            ): IDXResponse {
                return state.identify(remediationOption)
            }
        }

        object SelectEnrollProfile : Action() {
            override fun proceed(
                state: StepState,
                remediationOption: RemediationOption
            ): IDXResponse {
                return state.enroll(remediationOption)
            }
        }

        abstract fun proceed(state: StepState, remediationOption: RemediationOption): IDXResponse
    }

    class ViewModel internal constructor(
        internal val remediationOption: RemediationOption,
        val action: Action,
        val name: String,
    )

    override fun proceed(state: StepState): IDXResponse {
        return viewModel.action.proceed(state, viewModel.remediationOption)
    }

    override fun isValid(): Boolean {
        return true
    }
}

class ButtonViewFactory : ViewFactory<ButtonStep> {
    override fun createUi(
        references: ViewFactory.References,
        step: ButtonStep
    ): View {
        val binding = references.parent.inflateBinding(StepButtonBinding::inflate)
        binding.button.text = step.viewModel.name
        binding.button.setOnClickListener {
            references.callback.proceed(step)
        }
        return binding.root
    }
}
