package com.quanquan.quantoot.platform

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidPlatformServices(private val activity: ComponentActivity) : PlatformServices {
    override val preferences: KeyValueStore = AndroidPreferences(activity.applicationContext)
    override val secureStorage: KeyValueStore = AndroidSecureStorage(activity.applicationContext)
    private val redirects = Channel<String>(Channel.BUFFERED)
    override val oauthRedirects = redirects.receiveAsFlow()
    override val foreground = MutableStateFlow(true)
    override val appUpdates: AppUpdates = AndroidAppUpdates(activity)
    override val languageTag: String
        get() = Locale.getDefault().toLanguageTag()

    private var pendingPicker: CancellableContinuation<List<Uri>>? = null
    private var pendingSave: CancellableContinuation<Uri?>? = null

    private val picker =
        activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) {
            uris ->
            pendingPicker?.takeIf { it.isActive }?.resume(uris)
            pendingPicker = null
        }
    private val documentCreator =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri
            ->
            pendingSave?.takeIf { it.isActive }?.resume(uri)
            pendingSave = null
        }

    fun handleRedirect(uri: String?) {
        if (uri != null) redirects.trySend(uri)
    }

    override fun openAuthorization(url: String) {
        val uri = validatedUrl(url)
        require(uri.scheme == "https") { "Authorization requires HTTPS" }
        activity.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        )
    }

    override fun openUrl(url: String) {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW, validatedUrl(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        )
    }

    override fun share(text: String) {
        activity.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                null,
            )
        )
    }

    override suspend fun pickMedia(limit: Int): List<PickedMedia> {
        val uris = suspendCancellableCoroutine { continuation ->
            check(pendingPicker == null) { "A media picker is already open" }
            pendingPicker = continuation
            continuation.invokeOnCancellation { pendingPicker = null }
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
        return withContext(Dispatchers.IO) {
            uris.take(limit.coerceIn(1, 4)).map { uri ->
                val resolver = activity.contentResolver
                var name = "media"
                resolver
                    .query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0) ?: name
                            if (!cursor.isNull(1))
                                require(cursor.getLong(1) <= MAX_MEDIA_BYTES) {
                                    "Media exceeds 30 MiB"
                                }
                        }
                    }
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                require(mime.startsWith("image/") || mime.startsWith("video/")) {
                    "Choose an image or video"
                }
                val bytes =
                    resolver.openInputStream(uri)?.use { it.readBounded() }
                        ?: error("Could not read selected media")
                PickedMedia(name, mime, bytes)
            }
        }
    }

    override suspend fun saveMedia(url: String, name: String) {
        val safe = validatedUrl(url).toString()
        val downloaded =
            withContext(Dispatchers.IO) {
                val connection = URL(safe).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                try {
                    require(connection.responseCode in 200..299) { "Could not download media" }
                    require(connection.contentLengthLong <= MAX_MEDIA_BYTES) {
                        "Media exceeds 30 MiB"
                    }
                    val type = connection.contentType?.substringBefore(';') ?: "image/jpeg"
                    require(type.startsWith("image/")) { "Only images can be saved to the gallery" }
                    connection.inputStream.use { it.readBounded() } to type
                } finally {
                    connection.disconnect()
                }
            }
        saveDownloadedImage(downloaded, name)
    }

    override suspend fun saveImageBytes(bytes: ByteArray, name: String) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MEDIA_BYTES) { "Image exceeds 30 MiB" }
        saveDownloadedImage(bytes to "image/png", name)
    }

    private suspend fun saveDownloadedImage(downloaded: Pair<ByteArray, String>, name: String) {
        val cleanName =
            name.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank {
                "quantoot.jpg"
            }
        if (Build.VERSION.SDK_INT < 29) {
            val uri =
                suspendCancellableCoroutine<Uri?> { continuation ->
                    check(pendingSave == null) { "A save dialog is already open" }
                    pendingSave = continuation
                    continuation.invokeOnCancellation { pendingSave = null }
                    documentCreator.launch(cleanName)
                } ?: return
            withContext(Dispatchers.IO) {
                activity.contentResolver.openOutputStream(uri)?.use { it.write(downloaded.first) }
                    ?: error("Could not save image")
            }
            return
        }
        withContext(Dispatchers.IO) {
            val resolver = activity.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                    put(MediaStore.Images.Media.MIME_TYPE, downloaded.second)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QuanToot")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create image")
            try {
                resolver.openOutputStream(uri)?.use { it.write(downloaded.first) }
                    ?: error("Could not save image")
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    private fun validatedUrl(value: String): Uri {
        val uri = Uri.parse(value)
        require(
            uri.scheme in setOf("https", "http") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        ) {
            "Invalid web URL"
        }
        return uri
    }

    private fun InputStream.readBounded(): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(result.size().toLong() + count <= MAX_MEDIA_BYTES) { "Media exceeds 30 MiB" }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    private companion object {
        const val MAX_MEDIA_BYTES = 30L * 1024 * 1024
    }
}
