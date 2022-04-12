/*
 * Copyright 2021-Present Okta, Inc.
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
package sample.okta.android

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource
import com.okta.authfoundationbootstrap.CredentialBootstrap
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

class SampleApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()

        context = this

        Timber.plant(Timber.DebugTree())

        val clock = NetworkHeadersClock()
        AuthFoundationDefaults.clock = clock

        AuthFoundationDefaults.okHttpClientFactory = {
            val httpCacheDirectory = File(applicationContext.cacheDir, "http-cache")
            val cacheSize = 100 * 1024 * 1024 // 100 MiB
            val cache = Cache(httpCacheDirectory, cacheSize.toLong())

            OkHttpClient.Builder()
                .cache(cache)
                .hostnameVerifier { _, _ -> true }
                .addInterceptor(clock)
                .build()
        }

        val oidcConfiguration = OidcConfiguration(
            clientId = BuildConfig.CLIENT_ID,
            defaultScopes = setOf("openid", "email", "profile", "offline_access"),
            signInRedirectUri = BuildConfig.SIGN_IN_REDIRECT_URI,
            signOutRedirectUri = BuildConfig.SIGN_OUT_REDIRECT_URI,
        )
        val oidcClient = OidcClient.createFromDiscoveryUrl(
            oidcConfiguration,
            "${BuildConfig.ISSUER}/.well-known/openid-configuration".toHttpUrl(),
        )
        CredentialBootstrap.initialize(oidcClient.createCredentialDataSource(this))
    }
}

class NetworkHeadersClock : OidcClock, Interceptor {
    private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            // Tue, 12 Apr 2022 17:29:43 GMT
            return SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US)
        }
    }

    @Volatile private var offset: Long = 0

    override suspend fun currentTimeEpochSecond(): Long {
        return Instant.now().epochSecond + offset
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.networkResponse != null) { // We're not looking at a cached header.
            response.headers("date").firstOrNull()?.let { dateHeaderValue ->
                val date = dateFormat.get()?.parse(dateHeaderValue)
                if (date != null) {
                    offset = date.toInstant().epochSecond - Instant.now().epochSecond
                }
            }
        }
        return response
    }
}
