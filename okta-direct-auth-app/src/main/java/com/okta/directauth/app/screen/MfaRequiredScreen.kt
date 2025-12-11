package com.okta.directauth.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.app.model.mfaMethodSaver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MfaRequiredScreen(
    modifier: Modifier = Modifier,
    onVerify: (String, AuthMethod.Mfa) -> Unit,
) {
    var otp by rememberSaveable { mutableStateOf("") }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedMfaFactor by rememberSaveable(stateSaver = mfaMethodSaver) { mutableStateOf(AuthMethod.Mfa.Otp) }
    var otpFieldVisible by rememberSaveable { mutableStateOf(false) }
    var resumeButtonVisible by rememberSaveable { mutableStateOf(false) }

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
                    value = selectedMfaFactor.label,
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
                    listOf(AuthMethod.Mfa.Otp, AuthMethod.Mfa.OktaVerify, AuthMethod.Mfa.Sms, AuthMethod.Mfa.Voice, AuthMethod.Mfa.Passkeys).forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method.label) },
                            onClick = {
                                selectedMfaFactor = method
                                expanded = false
                                resumeButtonVisible = true
                                otpFieldVisible = method == AuthMethod.Mfa.Otp
                            }
                        )
                    }
                }
            }

            if (otpFieldVisible) {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it },
                    label = { Text("One-Time Password") },
                    singleLine = true
                )
            }

            if (resumeButtonVisible) {
                Button(
                    onClick = {
                        onVerify(otp, selectedMfaFactor)
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    if (selectedMfaFactor == AuthMethod.Mfa.OktaVerify) Text("Send push notification") else Text("Verify")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MfaRequiredScreenPreview() {
    MfaRequiredScreen { _, _ -> }
}
