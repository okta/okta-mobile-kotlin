/*
 * Copyright 2022-Present Okta, Inc.
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
package com.okta.sample.auth

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.composethemeadapter.MdcTheme
import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.form.Form
import kotlinx.coroutines.flow.Flow

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<AuthViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MdcTheme {
                MainUi(forms = viewModel.forms)
            }
        }
    }
}

@Composable
fun MainUi(forms: Flow<Form>) {
    val formState = forms.collectAsState(initial = null)
    formState.value?.let {
        FormUi(it)
    }
}

@Composable
fun FormUi(form: Form) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .verticalScroll(
                state = scrollState,
            )
            .padding(16.dp)
            .fillMaxHeight()
    ) {
        for (element in form.elements) {
            ElementUi(element)
        }
    }
}

@Composable
fun ElementUi(element: Element) {
    when (element) {
        is Element.Action -> {
            Button(onClick = element.onClick) {
                Text(text = element.text)
            }
        }
        is Element.Label -> {
            val fontSize = when (element.type) {
                Element.Label.Type.DESCRIPTION -> 16
                Element.Label.Type.HEADER -> 20
            }
            Text(text = element.text, fontSize = fontSize.sp)
        }
        is Element.TextInput -> {
            TextInputUi(element)
        }
        is Element.Options -> {
            OptionsUi(element)
        }
    }
}

@Composable
fun TextInputUi(element: Element.TextInput) {
    var text by remember { mutableStateOf(element.value) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    TextField(
        value = text,
        onValueChange = {
            text = it
            element.value = it
        },
        label = { Text(element.label) },
        singleLine = true,
        visualTransformation = if (passwordVisible || !element.isSecret) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = if (element.isSecret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        trailingIcon = {
            if (element.isSecret) {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff

                val description = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            }
        }
    )
}

@Composable
fun OptionsUi(element: Element.Options) {
    var selectedOption by remember { mutableStateOf(element.option) }

    for (option in element.options) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selectedOption == option, onClick = {
                selectedOption = option
                element.option = option
            })
            Text(text = option.label)
        }
        if (selectedOption == option) {
            Column(modifier = Modifier.padding(start = 10.dp)) {
                for (nestedElement in option.elements) {
                    ElementUi(element = nestedElement)
                }
            }
        }
    }
}
