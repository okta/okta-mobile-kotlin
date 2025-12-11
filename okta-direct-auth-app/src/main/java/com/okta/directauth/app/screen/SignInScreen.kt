package com.okta.directauth.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.AuthMethodSaver
import kotlinx.coroutines.Job

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    onVerify: (String, String, AuthMethod) -> Job?,
) {
    var loadingJob by rememberSaveable { mutableStateOf<Job?>(null) }
    var showLoading by rememberSaveable { mutableStateOf(false) }

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedAuthMethod by rememberSaveable(stateSaver = AuthMethodSaver) { mutableStateOf(AuthMethod.Password) }
    var usernameFieldVisible by rememberSaveable { mutableStateOf(selectedAuthMethod == AuthMethod.Password) }
    var passwordFieldVisible by rememberSaveable { mutableStateOf(selectedAuthMethod == AuthMethod.Password) }

    val supportedAuthMethods = listOf(AuthMethod.Password, AuthMethod.Mfa.Otp, AuthMethod.Mfa.OktaVerify, AuthMethod.Mfa.Sms, AuthMethod.Mfa.Voice, AuthMethod.Mfa.Passkeys)
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                TextField(
                    value = selectedAuthMethod.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sign in with") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    supportedAuthMethods.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method.label) },
                            onClick = {
                                selectedAuthMethod = method
                                expanded = false
                                usernameFieldVisible = true
                                passwordFieldVisible = method == AuthMethod.Password
                            }
                        )
                    }
                }
            }

            if (usernameFieldVisible) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true
                )
            }

            if (passwordFieldVisible) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff

                        val description = if (passwordVisible) "Hide password" else "Show password"

                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, description)
                        }
                    }
                )
            }

            if (selectedAuthMethod == AuthMethod.Mfa.Otp) {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it },
                    label = { Text("One-Time Password") },
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    val credential = if (selectedAuthMethod == AuthMethod.Mfa.Otp) otp else password
                    showLoading = true
                    loadingJob = onVerify(username.trim(), credential.trim(), selectedAuthMethod)
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Verify")
            }
        }

        if (showLoading) {
            LoadingDialog(onCancel = {
                loadingJob?.cancel()
                showLoading = false
            })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
    SignInScreen(onVerify = { _, _, _ -> null })
}