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
package com.okta.webauthenticationui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.net.toUri
import com.okta.authfoundation.events.EventCoordinator
import com.okta.webauthenticationui.events.CustomizeBrowserEvent
import com.okta.webauthenticationui.events.CustomizeCustomTabsEvent
import okhttp3.HttpUrl

/**
 * Default [WebAuthenticationProvider] implementation that launches the OIDC redirect flow using
 * Chrome Custom Tabs, preferring Chrome's Auth Tab (a Custom Tabs variant purpose-built for auth
 * redirect flows) when the resolved browser supports it.
 *
 * Browser selection and tab appearance are configured via constructor parameters rather than event
 * handlers. Use [preferredBrowsers] to control which browser is chosen, [queryIntentServicesFlags]
 * to adjust the package-manager query, [browserSelector] to fully override browser selection,
 * [customizeTabsIntent] to style the Chrome Custom Tab (toolbar color, animations, close button
 * icon, etc.), and [customizeAuthTabIntent] for the equivalent Auth Tab customization.
 *
 * ```kotlin
 * val provider = DefaultWebAuthenticationProvider(
 *     preferredBrowsers = listOf("com.android.chrome"),
 *     customizeTabsIntent = { context, builder ->
 *         builder.setToolbarColor(ContextCompat.getColor(context, R.color.brand_primary))
 *     }
 * )
 * val webAuth = WebAuthentication(client, provider)
 * ```
 */
class DefaultWebAuthenticationProvider @JvmOverloads constructor(
    /**
     * @param eventCoordinator legacy hook that still receives the deprecated [CustomizeBrowserEvent]/[CustomizeCustomTabsEvent];
     * new code should use the parameters below instead.
     */
    private val eventCoordinator: EventCoordinator = EventCoordinator(emptyList()),
    /**
     * The list of browser package names to prefer when selecting which Chrome Custom Tabs
     * implementation is used. Checked in order; the first installed match wins.
     *
     * Defaults to Chrome Stable, Chrome System, and Chrome Beta. Ignored if [browserSelector] is
     * overridden.
     */
    val preferredBrowsers: List<String> = DEFAULT_PREFERRED_BROWSERS,
    /**
     * Flags passed to [android.content.pm.PackageManager.queryIntentServices] when querying for Chrome Custom
     * Tabs intent services. Ignored if [browserSelector] is overridden.
     */
    val queryIntentServicesFlags: Int = 0,
    /**
     * Optional callback to customize the [androidx.browser.customtabs.CustomTabsIntent.Builder] before the Chrome Custom
     * Tab is launched. Use this to set toolbar color, animations, close button icon, and any
     * other [androidx.browser.customtabs.CustomTabsIntent.Builder] options.
     *
     * ```kotlin
     * DefaultWebAuthenticationProvider(
     *     customizeTabsIntent = { context, builder ->
     *         builder.setToolbarColor(ContextCompat.getColor(context, R.color.brand_primary))
     *     }
     * )
     * ```
     */
    private val customizeTabsIntent: ((context: Context, builder: CustomTabsIntent.Builder) -> Unit)? = null,
    /**
     * Optional callback to customize the [androidx.browser.auth.AuthTabIntent.Builder] before
     * the Auth Tab is launched. Only invoked when the resolved browser supports Auth Tab; has no
     * effect when falling back to Chrome Custom Tabs.
     *
     * ```kotlin
     * DefaultWebAuthenticationProvider(
     *     customizeAuthTabIntent = { _, builder ->
     *         builder.setEphemeralBrowsingEnabled(true)
     *     }
     * )
     * ```
     */
    private val customizeAuthTabIntent: ((context: Context, builder: AuthTabIntent.Builder) -> Unit)? = null,
    /**
     * [BrowserSelector] used to pick which browser package hosts the Chrome Custom Tab. Defaults
     * to the built-in selector, which honors [preferredBrowsers] and [queryIntentServicesFlags].
     * Override this to fully replace browser-selection logic, e.g. for testing.
     */
    private val browserSelector: BrowserSelector = defaultBrowserSelector(eventCoordinator, preferredBrowsers, queryIntentServicesFlags),
) : WebAuthenticationProvider,
    AuthTabWebAuthenticationProvider {
    companion object {
        /** HTTP header name used to send the Okta SDK user-agent string to the authorize endpoint. */
        const val X_OKTA_USER_AGENT = "X-Okta-User-Agent-Extended"

        /** The `X-Okta-User-Agent-Extended` header value sent with authorize requests. */
        val USER_AGENT_HEADER = "web-authentication-ui/${Build.VERSION.SDK_INT} com.okta.webauthenticationui/${BuildConfig.VERSION}"

        private const val CHROME_STABLE = "com.android.chrome"
        private const val CHROME_SYSTEM = "com.google.android.apps.chrome"
        private const val CHROME_BETA = "com.android.chrome.beta"

        /** The default preferred browser list: Chrome Stable, Chrome System, Chrome Beta. */
        val DEFAULT_PREFERRED_BROWSERS: List<String> = listOf(CHROME_STABLE, CHROME_SYSTEM, CHROME_BETA)

        private val BROWSER_VALIDATION_URIS = listOf("http://".toUri(), "https://".toUri())

        private fun defaultBrowserSelector(
            eventCoordinator: EventCoordinator,
            preferredBrowsers: List<String>,
            queryIntentServicesFlags: Int,
        ): BrowserSelector =
            BrowserSelector { context ->
                // Initialize the event from the constructor params so existing EventCoordinator
                // handlers that mutate CustomizeBrowserEvent continue to work as before.
                // New callers should use the constructor params directly instead of handling the event.
                @Suppress("DEPRECATION")
                val event = CustomizeBrowserEvent(preferredBrowsers = preferredBrowsers.toMutableList())
                @Suppress("DEPRECATION")
                event.queryIntentServicesFlags = queryIntentServicesFlags
                @Suppress("DEPRECATION")
                eventCoordinator.sendEvent(event)

                val pm: PackageManager = context.packageManager
                val serviceIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)

                @Suppress("DEPRECATION")
                val resolveInfoList = pm.queryIntentServices(serviceIntent, event.queryIntentServicesFlags)
                val customTabsBrowserPackages = resolveInfoList.mapNotNull { it.serviceInfo?.packageName }.toSet()

                // Some non-browser apps (e.g. embedded webview shells) also implement the Custom Tabs
                // service, so each candidate is confirmed to actually handle http(s) links before use.
                @Suppress("DEPRECATION")
                val preferredBrowser =
                    event.preferredBrowsers.firstOrNull { packageName ->
                        customTabsBrowserPackages.contains(packageName) && isBrowserPackage(pm, packageName)
                    }
                val selectedBrowser = preferredBrowser ?: customTabsBrowserPackages.firstOrNull { isBrowserPackage(pm, it) }

                selectedBrowser?.let { Result.success(it) } ?: Result.failure(NoBrowserFoundException())
            }

        private fun isBrowserPackage(
            pm: PackageManager,
            packageName: String,
        ): Boolean =
            BROWSER_VALIDATION_URIS.any { uri ->
                val intent =
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        setPackage(packageName)
                    }
                pm.queryIntentActivities(intent, 0).isNotEmpty()
            }
    }

    @Deprecated("Use launchCustomTab(context, url) to receive a Result-based outcome.", replaceWith = ReplaceWith("launchCustomTab(context, url)"))
    override fun launch(
        context: Context,
        url: HttpUrl,
    ): Exception? = launchCustomTab(context, url).fold({ null }, { it as? Exception ?: Exception(it) })

    override fun launchCustomTab(
        context: Context,
        url: HttpUrl,
    ): Result<Unit> {
        val intentBuilder: CustomTabsIntent.Builder = CustomTabsIntent.Builder()
        customizeTabsIntent?.invoke(context, intentBuilder)
        @Suppress("DEPRECATION")
        eventCoordinator.sendEvent(CustomizeCustomTabsEvent(context, intentBuilder))
        val tabsIntent: CustomTabsIntent = intentBuilder.build()

        val browserResult = browserSelector.selectBrowser(context)
        browserResult.getOrNull()?.let { tabsIntent.intent.setPackage(it) }

        val headers = Bundle()
        headers.putString(X_OKTA_USER_AGENT, USER_AGENT_HEADER)
        tabsIntent.intent.putExtra(Browser.EXTRA_HEADERS, headers)

        try {
            tabsIntent.launchUrl(context, url.toString().toUri())
            return Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            // If browser selection already failed (e.g. NoBrowserFoundException), that's a more
            // specific and actionable cause than the generic ActivityNotFoundException from the
            // unrestricted launch attempt above, so it's surfaced instead.
            return Result.failure(browserResult.exceptionOrNull() as? Exception ?: e)
        }
    }

    override fun launchAuthTab(
        context: Context,
        url: HttpUrl,
        redirectUrl: String,
        launcher: ActivityResultLauncher<Intent>,
    ): Result<Unit> {
        val packageBrowser = browserSelector.selectBrowser(context).getOrNull()
        if (packageBrowser == null || !CustomTabsClient.isAuthTabSupported(context, packageBrowser)) {
            return launchCustomTab(context, url)
        }

        val intentBuilder = AuthTabIntent.Builder()
        customizeAuthTabIntent?.invoke(context, intentBuilder)
        val authTabIntent = intentBuilder.build()
        authTabIntent.intent.setPackage(packageBrowser)

        val headers = Bundle()
        headers.putString(X_OKTA_USER_AGENT, USER_AGENT_HEADER)
        authTabIntent.intent.putExtra(Browser.EXTRA_HEADERS, headers)

        val redirectUri = redirectUrl.toUri()
        val authorizeUri = url.toString().toUri()

        return try {
            if (redirectUri.scheme == "https" || redirectUri.scheme == "http") {
                authTabIntent.launch(launcher, authorizeUri, redirectUri.host.orEmpty(), redirectUri.path.orEmpty())
            } else {
                authTabIntent.launch(launcher, authorizeUri, redirectUri.scheme.orEmpty())
            }
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(e)
        }
    }
}
