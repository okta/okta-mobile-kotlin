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
package com.okta.idx.android.network.mock

import com.okta.idx.android.network.OkHttpConfigurator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLSocketFactory

object OktaMockWebServer {
    private val okHttpConfigurationHelper = OkHttpConfigurationHelper()

    val mockingEnabled = AtomicBoolean(true)

    val mockWebServer: MockWebServer = MockWebServer()
    val dispatcher = NetworkDispatcher()
    val localhostCertificate: HeldCertificate = localhostCertificate()

    init {
        mockWebServer.dispatcher = dispatcher
        mockWebServer.useHttps(sslSocketFactory(localhostCertificate), false)
        mockWebServer.start()
        val mockBaseUrl = mockWebServer.url("")
        okHttpConfigurationHelper.baseUrlReference.set(mockBaseUrl)
    }

    private fun localhostCertificate(): HeldCertificate {
        return HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .commonName("Test Fixture")
            .build()
    }

    private fun sslSocketFactory(localhostCertificate: HeldCertificate): SSLSocketFactory {
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(localhostCertificate)
            .build()
        return serverCertificates.sslSocketFactory()
    }

    private class OkHttpConfigurationHelper : Interceptor, OkHttpConfigurator {
        val baseUrlReference = AtomicReference<HttpUrl>()

        override fun configure(clientBuilder: OkHttpClient.Builder) {
            if (!mockingEnabled.get()) {
                return
            }

            // This prevents Charles proxy from messing our mock responses.
            clientBuilder.proxy(Proxy.NO_PROXY)

            val handshakeCertificates = HandshakeCertificates.Builder()
                .addTrustedCertificate(localhostCertificate.certificate)
                .build()
            clientBuilder.sslSocketFactory(
                handshakeCertificates.sslSocketFactory(),
                handshakeCertificates.trustManager
            )
            clientBuilder.addInterceptor(this)
        }

        override fun built(client: OkHttpClient) {}

        override fun intercept(chain: Interceptor.Chain): Response {
            val baseUrl = baseUrlReference.get()
            var request = chain.request()
            val newUrl = request.url.newBuilder()
                .host(baseUrl.host)
                .scheme(baseUrl.scheme)
                .port(baseUrl.port)
                .build()
            request = request.newBuilder()
                .url(newUrl)
                .build()
            return chain.proceed(request)
        }
    }
}
