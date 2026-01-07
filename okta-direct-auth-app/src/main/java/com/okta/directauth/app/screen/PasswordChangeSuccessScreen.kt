package com.okta.directauth.app.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.R
import com.okta.directauth.app.ui.theme.Dimens
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme

/**
 * Success screen displayed after the user successfully changes their password.
 *
 * This screen shows a success message and provides a button to return to the
 * sign-in screen where they can use their new password.
 *
 * @param username The username of the account for which the password was changed
 * @param onBackToSignIn Callback invoked when user clicks "Back to Sign In" button
 */
@Composable
fun PasswordChangeSuccessScreen(
    username: String,
    onBackToSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(id = R.string.app_name),
            modifier = Modifier.size(Dimens.logoSize)
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        Text(
            text = "Password Changed Successfully",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your password has been successfully changed.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = username,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can now sign in with your new password.",
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        Button(
            onClick = onBackToSignIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Back to Sign In")
        }
    }
}

@PreviewLightDark
@Composable
fun PasswordChangeSuccessScreenPreview() {
    DirectAuthAppTheme {
        PasswordChangeSuccessScreen(
            username = "user1@okta.com",
            onBackToSignIn = {}
        )
    }
}
