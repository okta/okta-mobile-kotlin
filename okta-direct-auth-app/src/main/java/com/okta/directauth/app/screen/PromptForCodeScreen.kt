package com.okta.directauth.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job

@Composable
fun PromptForCodeScreen(
    modifier: Modifier = Modifier,
    onVerify: (passCode: String) -> Job?,
) {
    var code by rememberSaveable { mutableStateOf("") }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("One-Time Password") },
                singleLine = true
            )

            Button(
                onClick = {
                    onVerify.invoke(code)
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Verify")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PromptForCodeScreenPreview() {
    PromptForCodeScreen { null }
}
