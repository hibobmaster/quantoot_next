@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.quanquan.quantoot.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

actual fun createHttpClient(): HttpClient =
    HttpClient(Darwin) {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        install(WebSockets) {
            maxFrameSize = 512 * 1024
            pingIntervalMillis = 30_000
        }
    }

actual fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also { bytes ->
        require(size > 0)
        bytes.usePinned {
            check(
                SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) ==
                    errSecSuccess
            ) {
                "Secure random generator failed"
            }
        }
    }

actual fun sha256(bytes: ByteArray): ByteArray =
    ByteArray(32).also { digest ->
        bytes.usePinned { source ->
            digest.usePinned { output ->
                CC_SHA256(
                    if (bytes.isEmpty()) null else source.addressOf(0),
                    bytes.size.convert(),
                    output.addressOf(0).reinterpret(),
                )
            }
        }
    }
