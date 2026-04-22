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
package com.okta.oauth2

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalhostBrowserRedirectHandlerTest {
    private fun findAvailablePort(): Int {
        val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = socket.localPort
        socket.close()
        return port
    }

    private fun simulateRedirect(
        port: Int,
        requestLine: String,
    ) {
        // Retry connection until the server socket is listening
        var socket: Socket? = null
        for (attempt in 1..20) {
            try {
                socket = Socket(InetAddress.getLoopbackAddress(), port)
                break
            } catch (_: java.net.ConnectException) {
                Thread.sleep(50)
            }
        }
        checkNotNull(socket) { "Failed to connect to localhost:$port" }
        socket.getOutputStream().write("$requestLine\r\n".toByteArray())
        socket.getOutputStream().flush()
        // Read response to avoid connection reset
        socket.getInputStream().readAllBytes()
        socket.close()
    }

    @Test
    fun handleRedirect_CapturesCodeAndState() =
        runBlocking<Unit> {
            val port = findAvailablePort()
            val handler =
                LocalhostBrowserRedirectHandler(
                    port = port,
                    path = "/callback",
                    browserLauncher = { _ ->
                        Thread { simulateRedirect(port, "GET /callback?code=abc123&state=xyz HTTP/1.1") }.start()
                    }
                )

            val result = handler.handleRedirect("https://example.com/authorize")
            assertEquals("http://localhost:$port/callback?code=abc123&state=xyz", result)
        }

    @Test
    fun handleRedirect_NestedPath() =
        runBlocking<Unit> {
            val port = findAvailablePort()
            val handler =
                LocalhostBrowserRedirectHandler(
                    port = port,
                    path = "/callback/signin/myorg",
                    browserLauncher = { _ ->
                        Thread { simulateRedirect(port, "GET /callback/signin/myorg?code=abc123 HTTP/1.1") }.start()
                    }
                )

            val result = handler.handleRedirect("https://example.com/authorize")
            assertEquals("http://localhost:$port/callback/signin/myorg?code=abc123", result)
        }

    @Test
    fun handleRedirect_RejectsUnexpectedPath() =
        runBlocking<Unit> {
            val port = findAvailablePort()
            val handler =
                LocalhostBrowserRedirectHandler(
                    port = port,
                    path = "/callback",
                    browserLauncher = { _ ->
                        Thread { simulateRedirect(port, "GET /wrong?code=abc123 HTTP/1.1") }.start()
                    }
                )

            val exception =
                assertFailsWith<IllegalStateException> {
                    handler.handleRedirect("https://example.com/authorize")
                }
            assertTrue(exception.message!!.contains("Unexpected path"))
        }

    @Test
    fun handleRedirect_EmptyRequest_Fails() =
        runBlocking<Unit> {
            val port = findAvailablePort()
            val handler =
                LocalhostBrowserRedirectHandler(
                    port = port,
                    path = "/callback",
                    browserLauncher = { _ ->
                        Thread {
                            var socket: Socket? = null
                            for (attempt in 1..20) {
                                try {
                                    socket = Socket(InetAddress.getLoopbackAddress(), port)
                                    break
                                } catch (_: java.net.ConnectException) {
                                    Thread.sleep(50)
                                }
                            }
                            socket?.close()
                        }.start()
                    }
                )

            assertFailsWith<IllegalStateException> {
                handler.handleRedirect("https://example.com/authorize")
            }
        }

    @Test
    fun handleRedirect_Timeout() =
        runBlocking<Unit> {
            val port = findAvailablePort()
            val handler =
                LocalhostBrowserRedirectHandler(
                    port = port,
                    path = "/callback",
                    timeoutMs = 200,
                    browserLauncher = { }
                )

            assertFailsWith<TimeoutCancellationException> {
                handler.handleRedirect("https://example.com/authorize")
            }
        }

    @Test
    fun handleRedirect_PassesUrlToBrowserLauncher() =
        runBlocking<Unit> {
            val port = findAvailablePort()
            var launchedUrl: String? = null
            val handler =
                LocalhostBrowserRedirectHandler(
                    port = port,
                    path = "/callback",
                    browserLauncher = { url ->
                        launchedUrl = url
                        Thread { simulateRedirect(port, "GET /callback?code=abc HTTP/1.1") }.start()
                    }
                )

            handler.handleRedirect("https://example.com/authorize?client_id=test")
            assertEquals("https://example.com/authorize?client_id=test", launchedUrl)
        }

    @Test
    fun handleRedirect_CancellationClosesSocket() =
        runBlocking<Unit> {
            val port = findAvailablePort()
            val handler =
                LocalhostBrowserRedirectHandler(
                    port = port,
                    path = "/callback",
                    browserLauncher = { }
                )

            val deferred = async { handler.handleRedirect("https://example.com/authorize") }
            deferred.cancel()

            // Allow cancellation to propagate and close the socket
            Thread.sleep(100)

            // Port should be freed after cancellation — verify by binding it again
            val socket = ServerSocket(port, 1, InetAddress.getLoopbackAddress())
            socket.close()
        }
}
