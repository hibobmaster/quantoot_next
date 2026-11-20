@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.quanquan.quantoot.platform

import com.quanquan.quantoot.ui.AdaptiveWindow
import com.quanquan.quantoot.ui.VerticalControls
import com.quanquan.quantoot.ui.WindowRegion

import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.*
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.Foundation.*
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceGetType
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceCreationOptions
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.posix.memcpy

internal actual fun formatLocalDateTime(time: Instant, languageTag: String?): String =
    NSDateFormatter().apply {
        dateStyle = NSDateFormatterMediumStyle
        timeStyle = NSDateFormatterShortStyle
        languageTag?.let { locale = NSLocale(localeIdentifier = it) }
    }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(time.toEpochMilliseconds() / 1000.0))

class IosPlatformServices : PlatformServices {
    override val hideSensitivePosts = true
    override val preferences: AppPreferences = IosPreferences()
    override val secureStorage: KeyValueStore = IosSecureStorage()
    private val redirects = Channel<String>(Channel.BUFFERED)
    override val oauthRedirects = redirects.receiveAsFlow()
    override val foreground = MutableStateFlow(true)
    internal val adaptiveWindow = MutableStateFlow(AdaptiveWindow())
    override val languageTag: String
        get() = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowProvider: (() -> UIWindow?)? = null
    private var authorization: ASWebAuthenticationSession? = null
    private var pickerDelegate: PHPickerViewControllerDelegateProtocol? = null
    private val presentation =
        object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
            override fun presentationAnchorForWebAuthenticationSession(
                session: ASWebAuthenticationSession
            ): ASPresentationAnchor = window()
        }

    fun handleRedirect(url: String) {
        redirects.trySend(url)
    }

    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    fun updateWindowRegions(regions: List<WindowRegion>, controls: VerticalControls?) {
        adaptiveWindow.value = AdaptiveWindow(regions, controls)
    }

    fun setWindowProvider(provider: () -> UIWindow?) {
        windowProvider = provider
    }

    override fun openAuthorization(url: String) {
        val parsed = webUrl(url)
        require(parsed.scheme == "https") { "Authorization requires HTTPS" }
        authorization?.cancel()
        authorization =
            ASWebAuthenticationSession(parsed, "quantoot") { callback, _ ->
                    callback?.absoluteString?.let { redirects.trySend(it) }
                    authorization = null
                }
                .apply {
                    presentationContextProvider = presentation
                    prefersEphemeralWebBrowserSession = true
                    check(start()) { "Could not open the authorization browser" }
                }
    }

    override fun openUrl(url: String) {
        UIApplication.sharedApplication.openURL(
            webUrl(url),
            options = emptyMap<Any?, Any?>(),
            completionHandler = null,
        )
    }

    override suspend fun composeEmail(recipient: String): Boolean {
        val url = NSURL.URLWithString("mailto:$recipient") ?: return false
        return suspendCancellableCoroutine { continuation ->
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>()) { opened ->
                continuation.resume(opened)
            }
        }
    }

    override fun share(text: String) {
        val controller = UIActivityViewController(listOf(text), null)
        controller.popoverPresentationController?.let { popover ->
            popover.sourceView = presenter().view
            popover.sourceRect = presenter().view.bounds
        }
        presenter().presentViewController(controller, animated = true, completion = null)
    }

    override suspend fun confirmDiscard(title: String, discard: String, cancel: String): Boolean? {
        if (windowProvider?.invoke() == null) return null
        return suspendCancellableCoroutine { continuation ->
            val alert = UIAlertController.alertControllerWithTitle(title, null, UIAlertControllerStyleAlert)
            alert.addAction(UIAlertAction.actionWithTitle(cancel, UIAlertActionStyleCancel) {
                if (continuation.isActive) continuation.resume(false)
            })
            alert.addAction(UIAlertAction.actionWithTitle(discard, UIAlertActionStyleDestructive) {
                if (continuation.isActive) continuation.resume(true)
            })
            continuation.invokeOnCancellation {
                scope.launch {
                    if (alert.presentingViewController != null) alert.dismissViewControllerAnimated(true, null)
                }
            }
            presenter().presentViewController(alert, animated = true, completion = null)
        }
    }

    override suspend fun pickMedia(limit: Int): List<PickedMedia> =
        suspendCancellableCoroutine { continuation ->
            check(pickerDelegate == null) { "A media picker is already open" }
            val configuration =
                PHPickerConfiguration().apply {
                    selectionLimit = limit.coerceIn(1, 4).toLong()
                    filter =
                        PHPickerFilter.anyFilterMatchingSubfilters(
                            listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter)
                        )
                }
            val picker = PHPickerViewController(configuration)
            val delegate =
                object : NSObject(), PHPickerViewControllerDelegateProtocol {
                    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                        picker.dismissViewControllerAnimated(true, completion = null)
                        scope.launch {
                            try {
                                val media =
                                    didFinishPicking
                                        .filterIsInstance<PHPickerResult>()
                                        .take(limit)
                                        .map { loadPicked(it) }
                                if (continuation.isActive) continuation.resume(media)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            } finally {
                                pickerDelegate = null
                            }
                        }
                    }
                }
            pickerDelegate = delegate
            picker.delegate = delegate
            continuation.invokeOnCancellation {
                scope.launch {
                    picker.dismissViewControllerAnimated(true, completion = null)
                    pickerDelegate = null
                }
            }
            presenter().presentViewController(picker, animated = true, completion = null)
        }

    private suspend fun loadPicked(result: PHPickerResult): PickedMedia =
        suspendCancellableCoroutine { continuation ->
            val provider = result.itemProvider
            val type =
                if (provider.hasItemConformingToTypeIdentifier("public.movie")) "public.movie"
                else "public.image"
            provider.loadFileRepresentationForTypeIdentifier(type) { file, error ->
                try {
                    check(error == null && file != null) { "Could not read selected media" }
                    val attributes =
                        NSFileManager.defaultManager.attributesOfItemAtPath(
                            file.path.orEmpty(),
                            error = null,
                        )
                    val length = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0
                    require(length in 1..MAX_MEDIA_BYTES) { "Media exceeds 30 MiB" }
                    val data =
                        NSData.create(contentsOfURL = file)
                            ?: error("Could not read selected media")
                    val bytes = data.toBytes()
                    val name =
                        file.lastPathComponent
                            ?: if (type == "public.movie") "video.mp4" else "image.jpg"
                    val mime =
                        UTType.typeWithFilenameExtension(file.pathExtension.orEmpty())
                            ?.preferredMIMEType
                            ?: if (type == "public.movie") "video/mp4" else "image/jpeg"
                    if (continuation.isActive) continuation.resume(PickedMedia(name, mime, bytes))
                } catch (failure: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
            }
        }

    override suspend fun saveMedia(url: String, name: String) {
        val address = webUrl(url).absoluteString ?: error("Invalid media address")
        val bytes =
            withContext(Dispatchers.Default) {
                val client = createHttpClient()
                try {
                    client.prepareGet(address).execute { response ->
                        check(response.status.value in 200..299) { "Could not download image" }
                        val declared = response.headers["Content-Length"]?.toLongOrNull()
                        require(declared == null || declared <= MAX_MEDIA_BYTES) {
                            "Media exceeds 30 MiB"
                        }
                        val chunks = mutableListOf<ByteArray>()
                        val buffer = ByteArray(8192)
                        var total = 0
                        val channel = response.bodyAsChannel()
                        while (true) {
                            val count = channel.readAvailable(buffer)
                            if (count < 0) break
                            require(total.toLong() + count <= MAX_MEDIA_BYTES) {
                                "Media exceeds 30 MiB"
                            }
                            chunks += buffer.copyOf(count)
                            total += count
                        }
                        ByteArray(total).also { output ->
                            var offset = 0
                            chunks.forEach { chunk ->
                                chunk.copyInto(output, offset)
                                offset += chunk.size
                            }
                        }
                    }
                } finally {
                    client.close()
                }
            }
        saveImageBytes(bytes, name)
    }

    override suspend fun saveImageBytes(bytes: ByteArray, name: String) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MEDIA_BYTES) { "Image exceeds 30 MiB" }
        val data = bytes.toData()
        check(UIImage.imageWithData(data) != null) { "This file is not a supported image" }
        val dataRef = CFBridgingRetain(data)!!
        val type = try {
            val source = CGImageSourceCreateWithData(dataRef.reinterpret(), null)
                ?: error("This file is not a supported image")
            try {
                val identifier = CGImageSourceGetType(source)
                    ?: error("Could not identify the image format")
                UTType.typeWithIdentifier(CFBridgingRelease(CFRetain(identifier)) as String)
                    ?: error("This file is not a supported image")
            } finally {
                CFRelease(source)
            }
        } finally {
            CFRelease(dataRef)
        }
        val extension = type.preferredFilenameExtension
            ?: error("Could not identify the image filename extension")
        val baseName = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_', '-').take(120).ifBlank { "quantoot-image" }
        val options = PHAssetResourceCreationOptions().apply {
            originalFilename = "$baseName.$extension"
        }
        requestPhotoAddAccess()
        suspendCancellableCoroutine<Unit> { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                PHAssetCreationRequest.creationRequestForAsset()
                    .addResourceWithType(PHAssetResourceTypePhoto, data = data, options = options)
            }) { success, error ->
                if (continuation.isActive) {
                    if (success) continuation.resume(Unit)
                    else
                        continuation.resumeWithException(
                            IllegalStateException(error?.localizedDescription ?: "Could not save image to Photos")
                        )
                }
            }
        }
    }

    private suspend fun requestPhotoAddAccess() {
        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
        if (status == PHAuthorizationStatusAuthorized) return
        check(status == PHAuthorizationStatusNotDetermined) { "Allow Photos access to save this image" }
        suspendCancellableCoroutine<Unit> { continuation ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { result ->
                if (continuation.isActive) {
                    if (result == PHAuthorizationStatusAuthorized) continuation.resume(Unit)
                    else continuation.resumeWithException(
                        IllegalStateException("Allow Photos access to save this image")
                    )
                }
            }
        }
    }

    private fun webUrl(raw: String): NSURL {
        val url = NSURL.URLWithString(raw) ?: error("Invalid web URL")
        require(
            url.scheme in listOf("http", "https") &&
                !url.host.isNullOrBlank() &&
                url.user == null &&
                url.password == null
        ) {
            "Invalid web URL"
        }
        return url
    }

    private fun window(): UIWindow {
        return windowProvider?.invoke()
            ?: error("No application window")
    }

    internal fun presenter(): UIViewController {
        var controller = window().rootViewController ?: error("No root view controller")
        while (controller.presentedViewController != null) controller =
            controller.presentedViewController!!
        return controller
    }

    private fun NSData.toBytes(): ByteArray {
        require(length <= MAX_MEDIA_BYTES.toULong()) { "Media exceeds 30 MiB" }
        return ByteArray(length.toInt()).also { result ->
            if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
        }
    }

    private fun ByteArray.toData(): NSData =
        if (isEmpty()) NSData()
        else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

    private companion object {
        const val MAX_MEDIA_BYTES = 30L * 1024 * 1024
    }
}
