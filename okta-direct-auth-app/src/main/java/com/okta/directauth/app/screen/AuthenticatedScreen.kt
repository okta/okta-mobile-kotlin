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
package com.okta.directauth.app.screen

import android.util.Base64
import android.util.Base64.NO_PADDING
import android.util.Base64.NO_WRAP
import android.util.Base64.URL_SAFE
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import io.jsonwebtoken.Jwts

@Composable
fun AuthenticatedScreen(
    idToken: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayText =
        if (idToken.isBlank()) {
            "Empty id token, openid scope must be included in the request"
        } else {
            val idTokenJwt =
                idToken.let { token ->
                    // Suppose to validate the jws with the public keys in the jwks url oauth2/v1/keys.
                    // However in this example we convert the jws to jwt so jjwt can parse it as unsecured.
                    val (header, body, _) = token.split(".")
                    val unsecureHeader = String(Base64.decode(header, URL_SAFE)).replace("RS256", "none").replace("ES256", "none")
                    Base64.encodeToString(unsecureHeader.toByteArray(), URL_SAFE or NO_WRAP or NO_PADDING) + "." + body + "."
                }
            val payload =
                Jwts
                    .parser()
                    .unsecured()
                    .build()
                    .parseUnsecuredClaims(idTokenJwt)
                    .payload
            StringBuilder()
                .apply {
                    appendLine("iss: ${payload.get("iss", String::class.java)}")
                    appendLine("sub: ${payload.get("sub", String::class.java)}")
                    appendLine("name: ${payload.get("name", String::class.java)}")
                    appendLine("email: ${payload.get("email", String::class.java)}")
                }.toString()
        }

    Column(modifier = Modifier.padding(16.dp)) {
        LazyColumn(
            modifier =
                Modifier
                    .padding(16.dp)
                    .weight(1f)
        ) {
            items(1) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(displayText)
                    }
                }
            }
        }

        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Sign Out")
        }
    }
}

@PreviewLightDark
@Composable
private fun AuthenticatedScreenPreview() {
    DirectAuthAppTheme {
        AuthenticatedScreen(
            "eyJraWQiOiJLTC0tMnBSTGFWUVJOODNkdFAyZ0xqaUljSzN1d0F2YUJGcjJKYjBYU1dnIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHVmcGl6cXp2VFZ4ZDV6MDB3NiIsIm5hbWUiOiJhbmRyb2lkdGVzdDIgYW5kcm9pZHRlc3QyIiwiZW1haWwiOiJhbmRyb2lkdGVzdDJAb2t0YS5jb20iLCJ2ZXIiOjEsImlzcyI6Imh0dHBzOi8vYW5kcm9pZC1mY2hlbi10YzIuc2lnbWFuZXRjb3JwLnVzL29hdXRoMi9hdXNma3NjNWpJcU5WTEVvTjB3NiIsImF1ZCI6IjBvYWhxbmVxeEVmZzJPRGVsMHc2IiwiaWF0IjoxNzY2NDQ3MjA5LCJqdGkiOiJJRC41TlJJLXZkRVF5MHQzTmpnNEhvNmQ1bGpVVmZUWFVkNHYtVG5ua1ZnMTQiLCJhbXIiOlsic3drIiwib2t0YV92ZXJpZnkiXSwiaWRwIjoiMDBvZmtxeTV4TWN0c05EQmwwdzYiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJhbmRyb2lkdGVzdDJAb2t0YS5jb20iLCJhdXRoX3RpbWUiOjE3NjY0NDcyMDksImF0X2hhc2giOiItQlYyRG5CeEtMZTUyVC1uUmpkMWdBIn0.KkWcwA1MVujTQdKP1WPhrWZDxTFO5srbqJiHi2LQXMEhc2Mwb0FhhXL0SNIUfyOuU2Qcd7XIfIYJhopIaslEcqZy7e8nOou1aIWGYgVhiDg9_9BQ9ytD2tpn1m2-XAgn4tBU_P88StC4vAxyUwhsoEOxiO4QjuzlOF2hMfg7Nd5XCjY6Oum_CH5rMPSTlHxyvXqp04bHV107WCwMs90TupQyHPqjZoCH3Y-8fyaOc6nIJlawLZk0mlfuJtSjNzM4fT3n3Rdy7xTZemJ4hrjVyNMzmMinU7g1praH-o3WRwnuqXxsIIlcBT5yFHcDTkxXjozLnWY1PLSKnGA54owcUA",
            onSignOut = {}
        )
    }
}
