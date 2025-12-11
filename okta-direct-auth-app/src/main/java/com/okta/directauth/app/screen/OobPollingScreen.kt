package com.okta.directauth.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.viewModel.MainViewModel
import com.okta.directauth.model.Continuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@Composable
fun OobPollingScreen(
    modifier: Modifier = Modifier,
    message: String,
    countdownSeconds: Int? = null,
    pollAction: (() -> Job?)? = null, // Make pollAction nullable
    onCancel: () -> Unit,
) {
    var remainingTime by remember(countdownSeconds) { mutableIntStateOf(countdownSeconds ?: 0) }
    var pollingJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        pollingJob = pollAction?.invoke()
        if (countdownSeconds != null) {
            while (remainingTime > 0) {
                delay(1000)
                remainingTime--
            }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message)

            CircularProgressIndicator(modifier = Modifier.padding(16.dp))

            if (countdownSeconds != null) {
                Text("Expires in: $remainingTime seconds")
            }

            Button(
                onClick = {
                    pollingJob?.cancel()
                    onCancel()
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OobPollingScreenPreview() {
    OobPollingScreen(message = "Polling for result...", countdownSeconds = 60, onCancel = {}, pollAction = { null })
}
