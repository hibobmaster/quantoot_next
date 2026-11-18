package com.quanquan.quantoot.platform

import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal fun playbackFileExtension(url: String): String? {
    val extension = Url(url).encodedPath.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    if (extension == "m3u8" || extension == "mpd") return null
    return extension.takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) } ?: "mp4"
}

internal suspend fun downloadForPlayback(url: String, file: Path) = withContext(Dispatchers.Default) {
    createHttpClient().use { base ->
        base.config { followRedirects = true }.use { client ->
            client.prepareGet(url) {
                timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
            }.execute { response ->
                check(response.status.isSuccess())
                val channel = response.bodyAsChannel()
                SystemFileSystem.sink(file).buffered().use { output ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) {
                        val count = channel.readAvailable(bytes)
                        if (count < 0) break
                        output.write(bytes, 0, count)
                    }
                }
            }
        }
    }
}
