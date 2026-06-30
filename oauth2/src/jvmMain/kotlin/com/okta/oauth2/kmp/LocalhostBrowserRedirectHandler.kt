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
package com.okta.oauth2.kmp

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [BrowserRedirectHandler] implementation for JVM that starts a localhost HTTP server,
 * opens the system browser, and captures the redirect callback.
 *
 * The [port] and [path] must match the redirect URI registered in the Okta admin console.
 * For example, if registered as `http://localhost:8080/callback`, use `port = 8080` and `path = "/callback"`.
 *
 * @param port the port to listen on. Must match the port in the registered redirect URI.
 * @param path the expected callback path. Must match the path in the registered redirect URI.
 * @param timeoutMs the maximum time in milliseconds to wait for the redirect. Defaults to 5 minutes.
 * @param successResponse the raw HTTP response written back to the browser after the callback is
 *   captured. Defaults to [DEFAULT_SUCCESS_RESPONSE], a plain HTML page telling the user to close
 *   the window. Override to show custom branding or to issue a redirect, e.g.:
 *   `"HTTP/1.1 302 Found\r\nLocation: https://app.example.com/done\r\n\r\n"`.
 * @param browserLauncher opens the given URL in a browser. Defaults to [Desktop.browse].
 */
@Suppress("LongParameterList")
class LocalhostBrowserRedirectHandler
    @JvmOverloads
    constructor(
        private val port: Int,
        private val path: String,
        private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        private val successResponse: String = DEFAULT_SUCCESS_RESPONSE,
        private val browserLauncher: (String) -> Unit = Companion::defaultBrowserLauncher,
    ) : BrowserRedirectHandler {
        override suspend fun handleRedirect(url: String): String =
            withTimeout(timeoutMs.milliseconds) {
                suspendCancellableCoroutine { continuation ->
                    val serverSocket = ServerSocket(port, 1, InetAddress.getLoopbackAddress())

                    val thread =
                        Thread {
                            try {
                                val socket = serverSocket.accept()
                                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                                val requestLine = reader.readLine() ?: throw IllegalStateException("Empty request")

                                // Parse GET /callback?code=xxx&state=yyy HTTP/1.1
                                val requestPath =
                                    requestLine.split(" ").getOrNull(1)
                                        ?: throw IllegalStateException("Invalid request line: $requestLine")

                                if (!requestPath.startsWith(path)) {
                                    throw IllegalStateException("Unexpected path: $requestPath (expected: $path)")
                                }

                                val callbackUri = "http://localhost:$port$requestPath"

                                socket.getOutputStream().write(successResponse.toByteArray())
                                socket.close()

                                continuation.resume(callbackUri)
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                            } finally {
                                runCatching { serverSocket.close() }
                            }
                        }
                    thread.isDaemon = true
                    thread.start()

                    continuation.invokeOnCancellation {
                        runCatching { serverSocket.close() }
                        thread.interrupt()
                    }

                    browserLauncher(url)
                }
            }

        companion object {
            private const val DEFAULT_TIMEOUT_MS = 300_000L // 5 minutes

            /** Default HTTP response sent to the browser after a successful redirect capture. */
            const val DEFAULT_SUCCESS_RESPONSE: String =
                "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n" +
                    "<html><body><h1>Authentication Complete</h1>" +
                    "<p>You can close this window.</p></body></html>"

            /**
             * Creates a [LocalhostBrowserRedirectHandler] with a Java-friendly
             * [java.util.function.Consumer] browser launcher.
             *
             * Java usage:
             * ```java
             * LocalhostBrowserRedirectHandler handler =
             *     LocalhostBrowserRedirectHandler.create(8080, "/callback", url -> myBrowser.open(url));
             * ```
             *
             * @param port the port to listen on.
             * @param path the expected callback path.
             * @param browserLauncher a [Consumer] that opens the given URL in a browser.
             * @return a new [LocalhostBrowserRedirectHandler]
             */
            @JvmStatic
            fun create(
                port: Int,
                path: String,
                browserLauncher: Consumer<String>,
            ): LocalhostBrowserRedirectHandler = LocalhostBrowserRedirectHandler(port, path, browserLauncher = browserLauncher::accept)

            /**
             * Creates a [LocalhostBrowserRedirectHandler] with a custom timeout and
             * Java-friendly [java.util.function.Consumer] browser launcher.
             *
             * @param port the port to listen on.
             * @param path the expected callback path.
             * @param timeoutMs maximum wait time in milliseconds.
             * @param successResponse raw HTTP response written back to the browser on success.
             * @param browserLauncher a [Consumer] that opens the given URL in a browser.
             * @return a new [LocalhostBrowserRedirectHandler]
             */
            @JvmStatic
            fun create(
                port: Int,
                path: String,
                timeoutMs: Long,
                successResponse: String,
                browserLauncher: Consumer<String>,
            ): LocalhostBrowserRedirectHandler = LocalhostBrowserRedirectHandler(port, path, timeoutMs, successResponse, browserLauncher::accept)

            private fun defaultBrowserLauncher(url: String) {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw UnsupportedOperationException("Desktop browser launch not supported on this platform")
                }
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
