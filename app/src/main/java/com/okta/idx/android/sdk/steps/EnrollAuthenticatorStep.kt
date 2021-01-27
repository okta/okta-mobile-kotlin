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
            val options = mutableListOf<Option>()
            options += credentials.options[0].viewModelOptionSelect()
            options += credentials.options[1].viewModelOptionCustom()
            return ViewModel(options)
        }

        private fun Options.viewModelOptionSelect(): Option {
            val values = (value as OptionsForm).form.value
            val questionsFormValue = values.first { it.name == "questionKey" }
            val answerFormValue = values.first { it.name == "answer" }
            return Option.Select(
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
            return Option.Custom(
                answerLabel = answerFormValue.label,
                optionLabel = label,
                fieldLabel = questionFormValue.label,
                questionKey = questionKeyFormValue.value as String,
            )
        }
    }

    sealed class Option {
        data class Select(
            override val answerLabel: String,
            override val optionLabel: String,
            val fieldLabel: String,
            val questions: List<Question>,
            var selectedQuestion: Question? = null,
        ) : Option() {
            private val _questionErrorsLiveData = MutableLiveData<String>("")
            val questionErrorsLiveData: LiveData<String> = _questionErrorsLiveData

            override fun isValid(): Boolean {
                _questionErrorsLiveData.emitValidation { selectedQuestion != null }
                return selectedQuestion != null
            }
        }

        data class Custom(
            override val answerLabel: String,
            override val optionLabel: String,
            val fieldLabel: String,
            val questionKey: String,
            var question: String = "",
        ) : Option() {
            private val _questionErrorsLiveData = MutableLiveData<String>("")
            val questionErrorsLiveData: LiveData<String> = _questionErrorsLiveData

            override fun isValid(): Boolean {
                _questionErrorsLiveData.emitValidation { question.isNotEmpty() }
                return question.isNotEmpty()
            }
        }

        val viewId: Int by lazy { View.generateViewId() }

        abstract val answerLabel: String
        abstract val optionLabel: String
        abstract fun isValid(): Boolean
    }

    class Question internal constructor(
        val value: String,
        val label: String,
    )

    class ViewModel internal constructor(
        val options: List<Option>,
        var answer: String = "",
        var selectedOption: Option? = null,
    ) {
        private val _selectOptionErrorsLiveData = MutableLiveData<String>("")
        val selectOptionErrorsLiveData: LiveData<String> = _selectOptionErrorsLiveData

        private val _answerErrorsLiveData = MutableLiveData<String>("")
        val answerErrorsLiveData: LiveData<String> = _answerErrorsLiveData

        fun isValid(): Boolean {
            _selectOptionErrorsLiveData.emitValidation { selectedOption == null }
            _answerErrorsLiveData.emitValidation { answer.isEmpty() }
            return selectedOption?.isValid() ?: false && answer.isNotEmpty()
        }
    }

    override fun proceed(state: StepState): IDXResponse {
        return state.answer(Credentials().apply {
            when (val selectedOption = viewModel.selectedOption) {
                is Option.Custom -> {
                    questionKey = selectedOption.questionKey
                    question = selectedOption.question
                }
                is Option.Select -> {
                    questionKey = selectedOption.selectedQuestion!!.value
                }
            }
            answer = viewModel.answer.toCharArray()
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
            binding.radioGroup.addView(itemBinding.root)

            val contentView = when (option) {
                is EnrollAuthenticatorStep.Option.Select -> {
                    val optionBinding =
                        binding.radioGroup.inflateBinding(StepEnrollAuthenticatorSelectBinding::inflate)

                    optionBinding.titleTextView.text = option.fieldLabel
                    optionBinding.spinner.adapter = QuestionAdapter(option.questions)
                    optionBinding.spinner.onItemSelectedListener =
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
                        optionBinding.spinner.setSelection(option.questions.indexOf(it))
                    }

                    option.questionErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
                        optionBinding.errorTextView.text = errorMessage
                    }
                    optionBinding.root
                }
                is EnrollAuthenticatorStep.Option.Custom -> {
                    val optionBinding =
                        binding.radioGroup.inflateBinding(StepEnrollAuthenticatorCustomBinding::inflate)
                    optionBinding.textInputLayout.hint = option.fieldLabel
                    optionBinding.editText.setText(option.question)
                    optionBinding.editText.doOnTextChanged { question ->
                        option.question = question
                    }
                    option.questionErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
                        optionBinding.textInputLayout.error = errorMessage
                    }
                    optionBinding.root
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
                    binding.answerTextInputLayout.hint = option.answerLabel
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }

            binding.answerTextInputLayout.visibility = View.VISIBLE
        }

        viewModel.selectedOption?.also { selectedOption ->
            binding.radioGroup.check(selectedOption.viewId)
        }

        viewModel.selectOptionErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.radioGroupErrorTextView.text = errorMessage
        }

        binding.answerEditText.setText(viewModel.answer)
        binding.answerEditText.doOnTextChanged { answer ->
            viewModel.answer = answer
        }

        viewModel.answerErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.answerTextInputLayout.error = errorMessage
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
