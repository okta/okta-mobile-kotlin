package com.okta.idx.android.sdk.steps

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.okta.idx.android.databinding.StepSkipBinding
import com.okta.idx.android.sdk.Step
import com.okta.idx.android.sdk.StepFactory
import com.okta.idx.android.sdk.StepState
import com.okta.idx.android.sdk.ViewFactory
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.response.IDXResponse

class SkipStep private constructor(
    override val viewModel: ViewModel,
) : Step<SkipStep.ViewModel> {
    class Factory : StepFactory<ViewModel> {
        override fun get(remediationOption: RemediationOption): Step<ViewModel>? {
            if (remediationOption.name == "skip") {
                return SkipStep(ViewModel())
            }
            return null
        }
    }

    class ViewModel internal constructor()

    override fun proceed(state: StepState): IDXResponse {
        return state.skip()
    }

    override fun isValid(): Boolean {
        return true
    }
}

class SkipViewFactory : ViewFactory<SkipStep.ViewModel> {
    override fun createUi(
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
        viewModel: SkipStep.ViewModel
    ): View {
        val binding = parent.inflateBinding(StepSkipBinding::inflate)

        return binding.root
    }
}
