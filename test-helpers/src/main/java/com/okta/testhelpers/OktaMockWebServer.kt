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
package com.okta.testhelpers

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.Proxy
import javax.net.ssl.SSLSocketFactory

class OktaMockWebServer {
    private val mockWebServer: MockWebServer = MockWebServer()
    private val localhostCertificate: HeldCertificate = localhostCertificate()

    val dispatcher: NetworkDispatcher = NetworkDispatcher()
    val okHttpClient: OkHttpClient = okHttpClient()
    val baseUrl: HttpUrl

    init {
        mockWebServer.dispatcher = dispatcher
        mockWebServer.useHttps(sslSocketFactory(localhostCertificate), false)
        mockWebServer.start()
        baseUrl = mockWebServer.url("")
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

    private fun okHttpClient(): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()
        // This prevents Charles proxy from messing up our mock responses.
        clientBuilder.proxy(Proxy.NO_PROXY)

        val handshakeCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(localhostCertificate.certificate)
            .build()
        clientBuilder.sslSocketFactory(
            handshakeCertificates.sslSocketFactory(),
            handshakeCertificates.trustManager
        )
        return clientBuilder.build()
    }
}
