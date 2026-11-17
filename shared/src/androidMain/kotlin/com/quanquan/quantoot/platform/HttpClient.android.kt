package com.quanquan.quantoot.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import java.security.MessageDigest
import java.security.SecureRandom

actual fun createHttpClient(): HttpClient =
    HttpClient(OkHttp) {
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
    ByteArray(size).also { SecureRandom().nextBytes(it) }

actual fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
