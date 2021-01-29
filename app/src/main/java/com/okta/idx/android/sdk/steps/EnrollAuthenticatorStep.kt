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
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import com.okta.idx.android.R
import com.okta.idx.android.databinding.StepEnrollAuthenticatorBinding
import com.okta.idx.android.databinding.StepEnrollAuthenticatorCustomBinding
import com.okta.idx.android.databinding.StepEnrollAuthenticatorOptionBinding
import com.okta.idx.android.databinding.StepEnrollAuthenticatorPasscodeBinding
import com.okta.idx.android.databinding.StepEnrollAuthenticatorSelectBinding
import com.okta.idx.android.databinding.StepEnrollAuthenticatorSelectQuestionBinding
import com.okta.idx.android.sdk.Step
import com.okta.idx.android.sdk.StepFactory
import com.okta.idx.android.sdk.StepState
import com.okta.idx.android.sdk.ViewFactory
import com.okta.idx.android.sdk.util.doOnTextChanged
import com.okta.idx.android.sdk.util.emitValidation
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.sdk.api.model.Credentials
import com.okta.idx.sdk.api.model.FormValue
import com.okta.idx.sdk.api.model.Options
import com.okta.idx.sdk.api.model.OptionsForm
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.response.IDXResponse

class EnrollAuthenticatorStep private constructor(
    override val viewModel: ViewModel
) : Step<EnrollAuthenticatorStep.ViewModel> {
    class Factory : StepFactory<ViewModel> {
        override fun get(remediationOption: RemediationOption): Step<ViewModel>? {
            if (remediationOption.name == "enroll-authenticator") {
                return EnrollAuthenticatorStep(remediationOption.viewModel())
            }
            return null
        }

        private fun RemediationOption.viewModel(): ViewModel {
            val credentials = form().first { it.name == "credentials" }
            if (credentials.options != null) {
                val options = mutableListOf<Option>()
                options += credentials.options[0].viewModelOptionSelect()
                options += credentials.options[1].viewModelOptionCustom()
                return ViewModel(options)
            } else {
                val passcode = credentials.form.value.first { it.name == "passcode" }
                return ViewModel(listOf(passcode.viewModelOptionPasscode()))
            }
        }

        private fun Options.viewModelOptionSelect(): Option {
            val values = (value as OptionsForm).form.value
            val questionsFormValue = values.first { it.name == "questionKey" }
            val answerFormValue = values.first { it.name == "answer" }
            return Option.QuestionSelect(
                answerLabel = answerFormValue.label,
                optionLabel = label,
                fieldLabel = questionsFormValue.label,
                questions = questionsFormValue.questions(),
            )
        }

        private fun FormValue.questions(): List<Question> {
            val result = mutableListOf<Question>()
            for (option in options) {
                result += Question(
                    value = option.value as String,
                    label = option.label,
                )
            }
            return result
        }

        private fun Options.viewModelOptionCustom(): Option {
            val values = (value as OptionsForm).form.value
            val questionFormValue = values.first { it.name == "question" }
            val questionKeyFormValue = values.first { it.name == "questionKey" }
            val answerFormValue = values.first { it.name == "answer" }
            return Option.QuestionCustom(
                answerLabel = answerFormValue.label,
                optionLabel = label,
                fieldLabel = questionFormValue.label,
                questionKey = questionKeyFormValue.value as String,
            )
        }

        private fun FormValue.viewModelOptionPasscode(): Option {
            return Option.Passcode(
                optionLabel = label,
            )
        }
    }

    sealed class Option {
        data class QuestionSelect(
            override val optionLabel: String,
            val answerLabel: String,
            val fieldLabel: String,
            val questions: List<Question>,
            var selectedQuestion: Question? = null,
            var answer: String = "",
        ) : Option() {
            private val _questionErrorsLiveData = MutableLiveData<String>("")
            val questionErrorsLiveData: LiveData<String> = _questionErrorsLiveData

            private val _answerErrorsLiveData = MutableLiveData<String>("")
            val answerErrorsLiveData: LiveData<String> = _answerErrorsLiveData

            override fun isValid(): Boolean {
                val questionIsValid =
                    _questionErrorsLiveData.emitValidation { selectedQuestion != null }
                val answerIsValid = _answerErrorsLiveData.emitValidation { answer.isNotEmpty() }
                return questionIsValid && answerIsValid
            }
        }

        data class QuestionCustom(
            override val optionLabel: String,
            val answerLabel: String,
            val fieldLabel: String,
            val questionKey: String,
            var question: String = "",
            var answer: String = "",
        ) : Option() {
            private val _questionErrorsLiveData = MutableLiveData<String>("")
            val questionErrorsLiveData: LiveData<String> = _questionErrorsLiveData

            private val _answerErrorsLiveData = MutableLiveData<String>("")
            val answerErrorsLiveData: LiveData<String> = _answerErrorsLiveData

            override fun isValid(): Boolean {
                val questionIsValid =
                    _questionErrorsLiveData.emitValidation { question.isNotEmpty() }
                val answerIsValid = _answerErrorsLiveData.emitValidation { answer.isNotEmpty() }
                return questionIsValid && answerIsValid
            }
        }

        data class Passcode(
            override val optionLabel: String,
            var passcode: String = "",
            var confirmPasscode: String = "",
        ) : Option() {
            private val _errorsLiveData = MutableLiveData<String>("")
            val errorsLiveData: LiveData<String> = _errorsLiveData

            private val _confirmErrorsLiveData = MutableLiveData<String>("")
            val confirmErrorsLiveData: LiveData<String> = _confirmErrorsLiveData

            override fun isValid(): Boolean {
                val passcodeIsValid = _errorsLiveData.emitValidation { passcode.isNotEmpty() }
                val passcodesMatch =
                    _confirmErrorsLiveData.emitValidation("Passwords must match.") { passcode == confirmPasscode }
                return passcodeIsValid && passcodesMatch
            }
        }

        val viewId: Int by lazy { View.generateViewId() }

        abstract val optionLabel: String
        abstract fun isValid(): Boolean
    }

    class Question internal constructor(
        val value: String,
        val label: String,
    )

    class ViewModel internal constructor(
        val options: List<Option>,
        var selectedOption: Option? = defaultOption(options),
    ) {
        companion object {
            private fun defaultOption(options: List<Option>): Option? {
                return if (options.size == 1) {
                    options[0]
                } else {
                    null
                }
            }
        }

        private val _selectOptionErrorsLiveData = MutableLiveData<String>("")
        val selectOptionErrorsLiveData: LiveData<String> = _selectOptionErrorsLiveData

        fun isValid(): Boolean {
            val optionIsValid =
                _selectOptionErrorsLiveData.emitValidation { selectedOption != null }
            return optionIsValid && selectedOption?.isValid() ?: false
        }
    }

    override fun proceed(state: StepState): IDXResponse {
        return state.answer(Credentials().apply {
            when (val selectedOption = viewModel.selectedOption) {
                is Option.QuestionCustom -> {
                    questionKey = selectedOption.questionKey
                    question = selectedOption.question
                    answer = selectedOption.answer.toCharArray()
                }
                is Option.QuestionSelect -> {
                    questionKey = selectedOption.selectedQuestion!!.value
                    answer = selectedOption.answer.toCharArray()
                }
                is Option.Passcode -> {
                    passcode = selectedOption.passcode.toCharArray()
                }
            }
        })
    }

    override fun isValid(): Boolean {
        return viewModel.isValid()
    }
}

class EnrollAuthenticatorViewFactory : ViewFactory<EnrollAuthenticatorStep.ViewModel> {
    override fun createUi(
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
        viewModel: EnrollAuthenticatorStep.ViewModel
    ): View {
        val binding = parent.inflateBinding(StepEnrollAuthenticatorBinding::inflate)

        for (option in viewModel.options) {
            val itemBinding =
                binding.radioGroup.inflateBinding(StepEnrollAuthenticatorOptionBinding::inflate)
            itemBinding.radioButton.id = option.viewId
            itemBinding.radioButton.text = option.optionLabel
            if (viewModel.options.size == 1) {
                itemBinding.radioButton.visibility = View.GONE
            }
            binding.radioGroup.addView(itemBinding.root)

            val contentView = when (option) {
                is EnrollAuthenticatorStep.Option.QuestionSelect -> {
                    questionSelectView(option, binding.radioGroup, viewLifecycleOwner)
                }
                is EnrollAuthenticatorStep.Option.QuestionCustom -> {
                    questionCustomView(option, binding.radioGroup, viewLifecycleOwner)
                }
                is EnrollAuthenticatorStep.Option.Passcode -> {
                    passcodeView(option, binding.radioGroup, viewLifecycleOwner)
                }
            }
            contentView.visibility = View.GONE
            binding.radioGroup.addView(contentView)
            itemBinding.radioButton.setTag(R.id.enroll_item_content, contentView)
        }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.selectedOption = viewModel.options.first { it.viewId == checkedId }

            for (option in viewModel.options) {
                val contentView = binding.radioGroup
                    .findViewById<View>(option.viewId)
                    .getTag(R.id.enroll_item_content) as View

                contentView.visibility = if (option == viewModel.selectedOption) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }

        viewModel.selectedOption?.also { selectedOption ->
            binding.radioGroup.check(selectedOption.viewId)
        }

        viewModel.selectOptionErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.radioGroupErrorTextView.text = errorMessage
        }

        return binding.root
    }

    private fun questionSelectView(
        option: EnrollAuthenticatorStep.Option.QuestionSelect,
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
    ): View {
        val binding = parent.inflateBinding(StepEnrollAuthenticatorSelectBinding::inflate)

        binding.titleTextView.text = option.fieldLabel
        binding.spinner.adapter = QuestionAdapter(option.questions)
        binding.spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    option.selectedQuestion = null
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    option.selectedQuestion = option.questions[position]
                }
            }
        option.selectedQuestion?.also {
            binding.spinner.setSelection(option.questions.indexOf(it))
        }

        option.questionErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.errorTextView.text = errorMessage
        }

        binding.answerTextInputLayout.hint = option.answerLabel
        binding.answerEditText.setText(option.answer)
        binding.answerEditText.doOnTextChanged { answer ->
            option.answer = answer
        }

        option.answerErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.answerTextInputLayout.error = errorMessage
        }

        return binding.root
    }

    private fun questionCustomView(
        option: EnrollAuthenticatorStep.Option.QuestionCustom,
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
    ): View {
        val binding = parent.inflateBinding(StepEnrollAuthenticatorCustomBinding::inflate)
        binding.textInputLayout.hint = option.fieldLabel
        binding.editText.setText(option.question)
        binding.editText.doOnTextChanged { question ->
            option.question = question
        }
        option.questionErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.textInputLayout.error = errorMessage
        }

        binding.answerTextInputLayout.hint = option.answerLabel
        binding.answerEditText.setText(option.answer)
        binding.answerEditText.doOnTextChanged { answer ->
            option.answer = answer
        }

        option.answerErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.answerTextInputLayout.error = errorMessage
        }

        return binding.root
    }

    private fun passcodeView(
        option: EnrollAuthenticatorStep.Option.Passcode,
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
    ): View {
        val binding = parent.inflateBinding(StepEnrollAuthenticatorPasscodeBinding::inflate)
        binding.passcodeEditText.setText(option.passcode)
        binding.passcodeEditText.doOnTextChanged { passcode ->
            option.passcode = passcode
        }
        option.errorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.passcodeTextInputLayout.error = errorMessage
        }

        binding.confirmEditText.setText(option.confirmPasscode)
        binding.confirmEditText.doOnTextChanged { confirmPasscode ->
            option.confirmPasscode = confirmPasscode
        }
        option.confirmErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.confirmTextInputLayout.error = errorMessage
        }

        return binding.root
    }

    private class QuestionAdapter(
        private val questions: List<EnrollAuthenticatorStep.Question>
    ) : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val binding = if (convertView != null) {
                StepEnrollAuthenticatorSelectQuestionBinding.bind(convertView)
            } else {
                parent.inflateBinding(StepEnrollAuthenticatorSelectQuestionBinding::inflate)
            }

            binding.textView.text = questions[position].label

            return binding.root
        }

        override fun getItem(position: Int): EnrollAuthenticatorStep.Question {
            return questions[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return questions.size
        }
    }
}
