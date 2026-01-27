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
package com.okta.directauth.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val LightColorScheme =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightAccent2,
        onPrimaryContainer = LightOnSurfaceHighEmphasis,
        secondary = LightSecondary,
        onSecondary = LightOnPrimary,
        secondaryContainer = LightSurfaceTag,
        onSecondaryContainer = LightOnSurfaceHighEmphasis,
        tertiary = LightAccent1,
        onTertiary = LightOnPrimary,
        tertiaryContainer = LightAccent2,
        onTertiaryContainer = LightOnSurfaceHighEmphasis,
        error = LightDanger,
        onError = LightOnPrimary,
        errorContainer = LightSurfaceDanger,
        onErrorContainer = LightOnPrimary,
        background = LightBackgroundBase,
        onBackground = LightOnSurfaceHighEmphasis,
        surface = LightSurface,
        onSurface = LightOnSurfaceHighEmphasis,
        surfaceVariant = LightBackgroundInformation,
        onSurfaceVariant = LightOnSurfaceMediumEmphasis,
        outline = LightBorder,
        outlineVariant = LightAccent3,
        scrim = LightOnSurfaceMediumEmphasis,
        inverseSurface = LightOnSurfaceHighEmphasis,
        inverseOnSurface = LightSurface,
        inversePrimary = LightAccent1,
        surfaceTint = LightPrimary
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkAccent2,
        onPrimaryContainer = DarkOnSurfaceHighEmphasis,
        secondary = DarkSecondary,
        onSecondary = DarkSurface,
        secondaryContainer = DarkSurfaceTag,
        onSecondaryContainer = DarkOnSurfaceHighEmphasis,
        tertiary = DarkAccent1,
        onTertiary = DarkOnPrimary,
        tertiaryContainer = DarkAccent2,
        onTertiaryContainer = DarkOnSurfaceHighEmphasis,
        error = DarkDanger,
        onError = DarkOnPrimary,
        errorContainer = DarkSurfaceDanger,
        onErrorContainer = DarkOnPrimary,
        background = DarkBackgroundBase,
        onBackground = DarkOnSurfaceHighEmphasis,
        surface = DarkSurface,
        onSurface = DarkOnSurfaceHighEmphasis,
        surfaceVariant = DarkBackgroundInformation,
        onSurfaceVariant = DarkOnSurfaceMediumEmphasis,
        outline = DarkBorder,
        outlineVariant = DarkAccent3,
        scrim = DarkOnSurfaceMediumEmphasis,
        inverseSurface = DarkOnSurfaceHighEmphasis,
        inverseOnSurface = DarkSurface,
        inversePrimary = DarkAccent1,
        surfaceTint = DarkPrimary
    )

@Composable
fun DirectAuthAppTheme(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}
