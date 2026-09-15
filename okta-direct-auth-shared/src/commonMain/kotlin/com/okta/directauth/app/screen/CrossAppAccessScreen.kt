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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.okta.authfoundation.client.TokenInfo
import com.okta.directauth.app.model.ConfigValidation
import com.okta.directauth.app.model.CrossAppAccessConfig
import com.okta.directauth.app.model.CrossAppAccessState
import com.okta.directauth.app.model.IntrospectDisplay
import com.okta.directauth.app.model.SubjectKind
import com.okta.directauth.app.ui.XaaStrings
import com.okta.directauth.app.ui.theme.Dimens
import com.okta.directauth.app.ui.theme.DirectAuthAppTheme
import com.okta.directauth.app.util.FailureAttribution
import com.okta.oauth2.kmp.IdJagAssertion
import io.jsonwebtoken.Jwts

/**
 * Screen for the Cross App Access capability.
 *
 * Offers both a one-action exchange and a step-by-step mode that exposes the intermediate ID-JAG
 * for inspection and reuse. All user-facing text is drawn from [XaaStrings].
 *
 * @param config the resource app target configuration, shown before starting.
 * @param state the current [CrossAppAccessState].
 * @param onSignIn starts Cross App Access's own dedicated browser sign-in — the only way to
 *   obtain a subject; there is no pasted-token fallback.
 * @param onSelectSubjectKind called when the developer changes which subject kind to present.
 * @param onScopeInputChange called as the developer edits the scope text, taking precedence over
 *   any scope configured on the target itself — the org authorization server rejects a
 *   scope-less exchange, so this must be non-blank before starting one.
 * @param onExchange starts the one-action exchange.
 * @param onStart starts step-by-step mode (first step only).
 * @param onRedeem redeems the currently held ID-JAG — the second step, or a repeat redemption.
 * @param onIntrospect checks the resource access token with the resource authorization server —
 *   proves the token is actually accepted server-side, not just that the exchange succeeded.
 * @param onReset returns to a clean [CrossAppAccessState.Ready] without leaving this screen —
 *   used to retry with a different subject kind after a failure.
 * @param onBack leaves the screen entirely, returning to the home menu.
 */
@Composable
fun CrossAppAccessScreen(
    config: CrossAppAccessConfig,
    state: CrossAppAccessState,
    onSignIn: () -> Unit,
    onSelectSubjectKind: (SubjectKind) -> Unit,
    onScopeInputChange: (String) -> Unit,
    onExchange: () -> Unit,
    onStart: () -> Unit,
    onRedeem: () -> Unit,
    onIntrospect: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = XaaStrings.TITLE,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(
            text = XaaStrings.SCREEN_DESCRIPTION,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        when (state) {
            is CrossAppAccessState.NotConfigured -> {
                NotConfiguredSection(state.validation)
            }

            is CrossAppAccessState.Ready -> {
                ReadySection(
                    config = config,
                    state = state,
                    onSignIn = onSignIn,
                    onSelectSubjectKind = onSelectSubjectKind,
                    onScopeInputChange = onScopeInputChange,
                    onExchange = onExchange,
                    onStart = onStart
                )
            }

            is CrossAppAccessState.Working -> {
                WorkingSection(state.step)
            }

            is CrossAppAccessState.IdJagObtained -> {
                IdJagSection(idJag = state.idJag, redemptionCount = state.redemptionCount, onRedeem = onRedeem)
            }

            is CrossAppAccessState.ResourceTokenObtained -> {
                ResourceTokenSection(
                    tokenInfo = state.tokenInfo,
                    idJag = state.idJag,
                    redemptionCount = state.redemptionCount,
                    introspection = state.introspection,
                    onRedeem = onRedeem,
                    onIntrospect = onIntrospect
                )
            }

            is CrossAppAccessState.Failed -> {
                FailedSection(state = state, onReset = onReset)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        TextButton(onClick = onBack) {
            Text(text = XaaStrings.BACK_TO_HOME_BUTTON)
        }
    }
}

@Composable
private fun TargetSummarySection(config: CrossAppAccessConfig) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.spaceMedium)) {
            Text(text = XaaStrings.IDP_CLIENT_ID_LABEL, fontWeight = FontWeight.Bold)
            Text(text = config.idpClientId.orEmpty())
            Spacer(modifier = Modifier.height(Dimens.spaceSmall))
            Text(text = XaaStrings.IDP_ISSUER_LABEL, fontWeight = FontWeight.Bold)
            Text(text = config.idpIssuer.orEmpty())
            Spacer(modifier = Modifier.height(Dimens.spaceSmall))
            Text(text = XaaStrings.TARGET_LABEL, fontWeight = FontWeight.Bold)
            Text(text = config.issuer ?: config.authorizationServerId.orEmpty())
        }
    }
}

@Composable
private fun NotConfiguredSection(validation: ConfigValidation) {
    Column {
        Text(
            text = XaaStrings.NOT_CONFIGURED_TITLE,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        when (validation) {
            is ConfigValidation.NotConfigured -> {
                Text(text = XaaStrings.NOT_CONFIGURED_HINT, textAlign = TextAlign.Center)
            }

            is ConfigValidation.Incomplete -> {
                validation.missingDescriptions.forEach { description ->
                    Text(text = "• $description")
                }
            }

            ConfigValidation.Complete -> {
                Unit
            }
        }
    }
}

@Composable
private fun ReadySection(
    config: CrossAppAccessConfig,
    state: CrossAppAccessState.Ready,
    onSignIn: () -> Unit,
    onSelectSubjectKind: (SubjectKind) -> Unit,
    onScopeInputChange: (String) -> Unit,
    onExchange: () -> Unit,
    onStart: () -> Unit,
) {
    var viewTokenKind by remember { mutableStateOf<SubjectKind?>(null) }

    Column {
        TargetSummarySection(config)
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        if (!state.hasSession) {
            Text(text = XaaStrings.NO_SESSION_NOTE, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Text(text = XaaStrings.SIGN_IN_BUTTON)
            }
            return@Column
        }

        Text(text = XaaStrings.SUBJECT_LABEL, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        SubjectKind.entries.forEach { kind ->
            val available = state.availableSubjectKinds.contains(kind)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = state.selectedKind == kind, enabled = available, onClick = { onSelectSubjectKind(kind) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = state.selectedKind == kind, onClick = { onSelectSubjectKind(kind) }, enabled = available)
                Text(text = kind.name + if (!available) XaaStrings.SUBJECT_KIND_UNAVAILABLE_SUFFIX else "")
                TextButton(onClick = { viewTokenKind = kind }, enabled = available) {
                    Text(text = XaaStrings.VIEW_TOKEN_BUTTON)
                }
            }
        }
        Text(text = XaaStrings.SUBJECT_KIND_NOTE, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        Text(text = XaaStrings.SCOPE_LABEL, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = state.scopeInput,
            onValueChange = onScopeInputChange,
            placeholder = { Text(text = XaaStrings.SCOPE_PLACEHOLDER) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))

        val canStart = state.availableSubjectKinds.contains(state.selectedKind) && state.scopeInput.isNotBlank()

        Button(onClick = onExchange, modifier = Modifier.fillMaxWidth(), enabled = canStart) {
            Text(text = XaaStrings.ONE_ACTION_BUTTON)
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = canStart) {
            Text(text = XaaStrings.STEP_ONE_BUTTON)
        }
    }

    val dialogKind = viewTokenKind
    val dialogTokenValue = state.session?.let { dialogKind?.valueIn(it) }
    if (dialogKind != null && dialogTokenValue != null) {
        TokenInfoDialog(kind = dialogKind, tokenValue = dialogTokenValue, onDismiss = { viewTokenKind = null })
    }
}

@Composable
private fun TokenInfoDialog(
    kind: SubjectKind,
    tokenValue: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${kind.name} ${XaaStrings.TOKEN_DIALOG_TITLE_SUFFIX}") },
        text = {
            Text(
                text = buildTokenClaimsText(tokenValue),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = XaaStrings.TOKEN_DIALOG_CLOSE_BUTTON)
            }
        }
    )
}

/**
 * Decodes [rawToken]'s claims for local display only — never verifies the signature, since this
 * is a read-only preview of a token the sample already holds, not a trust decision. Forces the
 * header's `alg` to `none` before parsing so [Jwts]'s unsecured parser accepts a signed JWT
 * without needing (or checking) its key, mirroring the ID token display pattern used elsewhere in
 * this sample. Falls back to a raw dump for access/refresh tokens that are opaque (not JWTs) —
 * Cross App Access does not require any of the three token kinds to be JWTs.
 */
private fun buildTokenClaimsText(rawToken: String): String {
    val parts = rawToken.split(".")
    if (parts.size != 3) {
        return "${XaaStrings.TOKEN_DIALOG_NOT_A_JWT_NOTE}\n\n$rawToken"
    }
    return runCatching {
        val urlSafeNoPad =
            kotlin.io.encoding.Base64.UrlSafe
                .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
        val unsecureHeader =
            urlSafeNoPad
                .decode(parts[0])
                .decodeToString()
                .replace("RS256", "none")
                .replace("ES256", "none")
        val unsecuredJwt = urlSafeNoPad.encode(unsecureHeader.toByteArray()) + "." + parts[1] + "."
        val claims =
            Jwts
                .parser()
                .unsecured()
                .build()
                .parseUnsecuredClaims(unsecuredJwt)
                .payload

        buildString {
            appendLine(XaaStrings.TOKEN_DIALOG_CLAIMS_HEADER)
            claims.entries.sortedBy { it.key }.forEach { (key, value) ->
                appendLine("$key: $value")
            }
        }
    }.getOrElse { "${XaaStrings.TOKEN_DIALOG_PARSE_FAILURE_PREFIX} ${it.message}\n\n$rawToken" }
}

@Composable
private fun WorkingSection(step: CrossAppAccessState.Working.Step) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(
            text =
                when (step) {
                    CrossAppAccessState.Working.Step.SIGNING_IN -> XaaStrings.SIGNING_IN_NOTE
                    CrossAppAccessState.Working.Step.OBTAINING_ID_JAG -> "Obtaining ${XaaStrings.ID_JAG_TERM}..."
                    CrossAppAccessState.Working.Step.REDEEMING -> "Redeeming..."
                    CrossAppAccessState.Working.Step.INTROSPECTING -> XaaStrings.INTROSPECTING_NOTE
                }
        )
    }
}

@Composable
private fun IdJagSection(
    idJag: IdJagAssertion,
    redemptionCount: Int,
    onRedeem: () -> Unit,
) {
    Column {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dimens.spaceMedium)) {
                Text(text = XaaStrings.ID_JAG_SECTION_TITLE, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(Dimens.spaceSmall))
                Text(text = "${XaaStrings.ID_JAG_AUDIENCE_LABEL}: ${idJag.audience}")
                Text(text = "${XaaStrings.ID_JAG_LIFETIME_LABEL}: ${XaaStrings.formatSeconds(idJag.expiresIn)}")
                Text(text = "${XaaStrings.ID_JAG_SCOPE_LABEL}: ${idJag.scope ?: "(none reported)"}")
                Text(text = "${XaaStrings.ID_JAG_REDEMPTION_COUNT_LABEL}: $redemptionCount")
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(text = XaaStrings.ID_JAG_REUSE_NOTE, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Button(onClick = onRedeem, modifier = Modifier.fillMaxWidth()) {
            Text(text = XaaStrings.REDEEM_BUTTON)
        }
    }
}

@Composable
private fun ResourceTokenSection(
    tokenInfo: TokenInfo,
    idJag: IdJagAssertion?,
    redemptionCount: Int,
    introspection: IntrospectDisplay?,
    onRedeem: () -> Unit,
    onIntrospect: () -> Unit,
) {
    Column {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Dimens.spaceMedium)) {
                Text(text = XaaStrings.RESULT_SECTION_TITLE, fontWeight = FontWeight.Bold)
                Text(text = XaaStrings.RESULT_FOR_RESOURCE_APP_NOTE, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(Dimens.spaceSmall))
                Text(text = "${XaaStrings.RESULT_SCOPE_LABEL}: ${tokenInfo.scope ?: "(none reported)"}")
                Text(text = "${XaaStrings.RESULT_TOKEN_TYPE_LABEL}: ${tokenInfo.tokenType}")
                Text(text = "${XaaStrings.RESULT_EXPIRY_LABEL}: ${XaaStrings.formatSeconds(tokenInfo.expiresIn)}")
            }
        }

        // Only step-by-step results carry the ID-JAG that produced them — a one-action result has
        // none to redeem again, since exchange() does not retain one for reuse.
        if (idJag != null) {
            Spacer(modifier = Modifier.height(Dimens.spaceSmall))
            Text(text = "${XaaStrings.ID_JAG_REDEMPTION_COUNT_LABEL}: $redemptionCount")
            Spacer(modifier = Modifier.height(Dimens.spaceMedium))
            Button(onClick = onRedeem, modifier = Modifier.fillMaxWidth()) {
                Text(text = XaaStrings.REDEEM_AGAIN_BUTTON)
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        if (introspection != null) {
            Text(text = "${XaaStrings.INTROSPECT_ACTIVE_LABEL}: ${if (introspection.active) "Yes" else "No"}", fontWeight = FontWeight.Bold)
        } else {
            OutlinedButton(onClick = onIntrospect, modifier = Modifier.fillMaxWidth()) {
                Text(text = XaaStrings.INTROSPECT_BUTTON)
            }
        }
    }
}

@Composable
private fun FailedSection(
    state: CrossAppAccessState.Failed,
    onReset: () -> Unit,
) {
    Column {
        Text(text = XaaStrings.FAILURE_TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(Dimens.spaceSmall))
        Text(
            text =
                when (state.attribution) {
                    FailureAttribution.IDENTITY_PROVIDER -> "Identity Provider"
                    FailureAttribution.RESOURCE_SERVER -> "Resource Authorization Server"
                    FailureAttribution.CONFIGURATION -> "Configuration"
                },
            fontWeight = FontWeight.Bold
        )
        Text(text = state.serverMessage, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(Dimens.spaceMedium))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (state.serverMessage.contains("expired", ignoreCase = true)) XaaStrings.RESTART_AFTER_EXPIRY_BUTTON else XaaStrings.TRY_AGAIN_BUTTON)
        }
    }
}

private val previewConfig =
    CrossAppAccessConfig(
        idpIssuer = "https://idp.example.okta.com",
        idpClientId = "idp-client-id",
        issuer = "https://resource.example.okta.com",
        authorizationServerId = null,
        clientId = null,
        resource = null
    )

private val previewIdJag =
    IdJagAssertion.restore(
        value = "preview-id-jag-value",
        audience = "https://resource.example.okta.com",
        expiresIn = 60,
        issuedAt = 0L,
        scope = "openid profile"
    )

private class PreviewTokenInfo(
    override val scope: String?,
) : TokenInfo {
    override val id: String = "preview-token"
    override val clientId: String = "preview-client-id"
    override val issuerUrl: String = "https://resource.example.okta.com"
    override val tokenType: String = "Bearer"
    override val expiresIn: Int = 3600
    override val accessToken: String = "preview-access-token"
    override val refreshToken: String? = null
    override val idToken: String? = null
    override val deviceSecret: String? = null
    override val issuedTokenType: String? = null
}

@Preview
@Composable
private fun CrossAppAccessScreenNotConfiguredPreview() {
    DirectAuthAppTheme {
        CrossAppAccessScreen(
            config = previewConfig,
            state = CrossAppAccessState.NotConfigured(ConfigValidation.Incomplete(listOf("xaaIdpIssuer (the requesting app's own org)"))),
            onSignIn = {},
            onSelectSubjectKind = {},
            onScopeInputChange = {},
            onExchange = {},
            onStart = {},
            onRedeem = {},
            onIntrospect = {},
            onReset = {},
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun CrossAppAccessScreenReadyNoSessionPreview() {
    DirectAuthAppTheme {
        CrossAppAccessScreen(
            config = previewConfig,
            state = CrossAppAccessState.Ready(availableSubjectKinds = emptyList(), selectedKind = SubjectKind.IDENTITY, hasSession = false),
            onSignIn = {},
            onSelectSubjectKind = {},
            onScopeInputChange = {},
            onExchange = {},
            onStart = {},
            onRedeem = {},
            onIntrospect = {},
            onReset = {},
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun CrossAppAccessScreenReadySignedInPreview() {
    DirectAuthAppTheme {
        CrossAppAccessScreen(
            config = previewConfig,
            state =
                CrossAppAccessState.Ready(
                    availableSubjectKinds = listOf(SubjectKind.IDENTITY, SubjectKind.ACCESS),
                    selectedKind = SubjectKind.IDENTITY,
                    hasSession = true,
                    scopeInput = "openid profile"
                ),
            onSignIn = {},
            onSelectSubjectKind = {},
            onScopeInputChange = {},
            onExchange = {},
            onStart = {},
            onRedeem = {},
            onIntrospect = {},
            onReset = {},
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun CrossAppAccessScreenWorkingPreview() {
    DirectAuthAppTheme {
        CrossAppAccessScreen(
            config = previewConfig,
            state = CrossAppAccessState.Working(CrossAppAccessState.Working.Step.OBTAINING_ID_JAG),
            onSignIn = {},
            onSelectSubjectKind = {},
            onScopeInputChange = {},
            onExchange = {},
            onStart = {},
            onRedeem = {},
            onIntrospect = {},
            onReset = {},
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun CrossAppAccessScreenIdJagObtainedPreview() {
    DirectAuthAppTheme {
        CrossAppAccessScreen(
            config = previewConfig,
            state = CrossAppAccessState.IdJagObtained(idJag = previewIdJag, redemptionCount = 0),
            onSignIn = {},
            onSelectSubjectKind = {},
            onScopeInputChange = {},
            onExchange = {},
            onStart = {},
            onRedeem = {},
            onIntrospect = {},
            onReset = {},
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun CrossAppAccessScreenResourceTokenObtainedPreview() {
    DirectAuthAppTheme {
        CrossAppAccessScreen(
            config = previewConfig,
            state = CrossAppAccessState.ResourceTokenObtained(tokenInfo = PreviewTokenInfo(scope = "openid profile"), idJag = previewIdJag, redemptionCount = 1),
            onSignIn = {},
            onSelectSubjectKind = {},
            onScopeInputChange = {},
            onExchange = {},
            onStart = {},
            onRedeem = {},
            onIntrospect = {},
            onReset = {},
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun CrossAppAccessScreenFailedPreview() {
    DirectAuthAppTheme {
        CrossAppAccessScreen(
            config = previewConfig,
            state =
                CrossAppAccessState.Failed(
                    attribution = FailureAttribution.RESOURCE_SERVER,
                    serverMessage = "invalid_scope: The requested scope is not valid for this resource."
                ),
            onSignIn = {},
            onSelectSubjectKind = {},
            onScopeInputChange = {},
            onExchange = {},
            onStart = {},
            onRedeem = {},
            onIntrospect = {},
            onReset = {},
            onBack = {}
        )
    }
}
