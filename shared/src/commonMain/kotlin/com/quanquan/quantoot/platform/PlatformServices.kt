package com.quanquan.quantoot.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface KeyValueStore {
    @Throws(Exception::class) suspend fun read(key: String): String?

    @Throws(Exception::class) suspend fun write(key: String, value: String?)
}

data class PickedMedia(val name: String, val mimeType: String, val bytes: ByteArray)

interface PlatformServices {
    val preferences: KeyValueStore
    val secureStorage: KeyValueStore
    val oauthRedirects: Flow<String>
    val foreground: StateFlow<Boolean>
    val languageTag: String
    val appUpdates: AppUpdates? get() = null
    val hideSensitivePosts: Boolean get() = false

    fun openAuthorization(url: String)

    fun openUrl(url: String)

    suspend fun composeEmail(recipient: String): Boolean

    fun share(text: String)

    suspend fun pickMedia(limit: Int = 4): List<PickedMedia>

    suspend fun saveMedia(url: String, name: String)

    suspend fun saveImageBytes(bytes: ByteArray, name: String)
}

expect fun createHttpClient(): HttpClient

expect fun secureRandomBytes(size: Int): ByteArray

expect fun sha256(bytes: ByteArray): ByteArray

@Composable expect fun NativeVideoPlayer(url: String, modifier: Modifier, onError: () -> Unit)
