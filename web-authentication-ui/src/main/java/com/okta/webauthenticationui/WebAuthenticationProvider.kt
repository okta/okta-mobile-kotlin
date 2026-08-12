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

import android.app.Activity
import android.content.Context
import okhttp3.HttpUrl

/**
 * Used to launch the OIDC redirect flow associated with a [WebAuthentication].
 *
 * Most integrators should use [DefaultWebAuthenticationProvider]; implement this only to fully
 * control how the authorize URL is presented. Only [launch] is abstract; [launchCustomTab] has a
 * default implementation built on it, so implementations only need to provide [launch].
 */
interface WebAuthenticationProvider {
    /**
     * Launches the OIDC redirect flow associated with a [WebAuthentication].
     *
     * @param context the Android [Activity] [Context] which is used to display the flow.
     * @param url the url the instance should display.
     *
     * @return `null` if the flow launched successfully, or the [Exception] that caused the launch to fail.
     */
    @Deprecated(
        message = "Use launchCustomTab(context, url) to receive a Result-based outcome.",
        replaceWith = ReplaceWith("launchCustomTab(context, url)")
    )
    fun launch(
        context: Context,
        url: HttpUrl,
    ): Exception?

    /**
     * Launches the OIDC redirect flow associated with a [WebAuthentication] and wraps launch
     * success/failure in a [Result].
     *
     * Has a default implementation that delegates to the deprecated [launch], so existing
     * [WebAuthenticationProvider] implementations keep compiling unchanged; override this instead
     * of [launch] in new implementations.
     *
     * @param context the Android [Activity] [Context] used to display the flow.
     * @param url the authorize URL the instance should display.
     *
     * @return [Result.success] when the flow launches successfully, or [Result.failure] with the
     * launch exception when it fails.
     */
    @Suppress("DEPRECATION")
    fun launchCustomTab(
        context: Context,
        url: HttpUrl,
    ): Result<Unit> = launch(context, url)?.let { Result.failure(it) } ?: Result.success(Unit)
}
