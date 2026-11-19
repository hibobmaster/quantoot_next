package com.quanquan.quantoot.platform

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.quanquan.quantoot.data.apiJson
import com.quanquan.quantoot.data.boundedJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readBuffer
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.decodeFromJsonElement

class AppUpdateFileProvider : FileProvider()

internal class AndroidAppUpdates(private val activity: ComponentActivity) : AppUpdates {
    private val packageManager = activity.packageManager
    private val packageName = activity.packageName
    private val cache = File(activity.cacheDir, "app-updates")
    private val lock = Mutex()
    private var staged: Pair<AppUpdate, File>? = null

    override val currentVersion: String by lazy {
        installedPackage().versionName.orEmpty()
    }

    override suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        createHttpClient().use { client ->
            val body = client.readText(LATEST_RELEASE, 512 * 1024, ContentType.Application.Json)
            apiJson.decodeFromJsonElement<AndroidRelease>(boundedJson(body))
                .updateFor(currentVersion, Build.SUPPORTED_ABIS.toList())
        }
    }

    override suspend fun download(update: AppUpdate, onProgress: (Int) -> Unit) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                if (!update.isValid() || update.abi !in Build.SUPPORTED_ABIS) throw InvalidUpdatePackage()
                staged = null
                check(cache.isDirectory || cache.mkdirs())
                cache.listFiles()?.forEach { it.delete() }
                val partial = File.createTempFile("download-", ".apk", cache)
                try {
                    createHttpClient().use { client ->
                        val manifest = client.readText(update.downloadUrl(UPDATE_CHECKSUMS), 8 * 1024)
                        val checksum = updateChecksum(manifest, update.apkName)
                        val actual = client.downloadApk(update, partial, onProgress)
                        if (actual != checksum) throw InvalidUpdatePackage()
                    }
                    validateApk(partial, update)
                    val apk = File(cache, update.apkName)
                    check(partial.renameTo(apk))
                    staged = update to apk
                } finally {
                    partial.delete()
                }
            }
        }
    }

    override fun canInstall(): Boolean = packageManager.canRequestPackageInstalls()

    override fun openInstallSettings() {
        launchSystemActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", packageName, null)))
    }

    override suspend fun install(update: AppUpdate) {
        lock.withLock {
            val apk = staged?.takeIf { it.first == update }?.second ?: throw InvalidUpdatePackage()
            if (withContext(Dispatchers.IO) { !apk.isFile || apk.length() != update.bytes }) throw InvalidUpdatePackage()
            check(canInstall())
            val uri = FileProvider.getUriForFile(activity, "$packageName.updates", apk)
            launchSystemActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }

    @Suppress("DEPRECATION")
    private fun launchSystemActivity(intent: Intent) {
        val handler = checkNotNull(packageManager.resolveActivity(
            intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
        ))
        activity.startActivity(intent.setComponent(ComponentName(handler.activityInfo.packageName, handler.activityInfo.name)))
    }

    @Suppress("DEPRECATION")
    private fun installedPackage(flags: Int = 0): PackageInfo = packageManager.getPackageInfo(packageName, flags)

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, update: AppUpdate) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = packageManager.getPackageArchiveInfo(file.path, flags) ?: throw InvalidUpdatePackage()
        val installed = installedPackage(flags)
        val signers = candidate.currentSigners()
        if (candidate.packageName != packageName || candidate.versionName != update.version ||
            PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed) ||
            signers.isEmpty() || signers != installed.currentSigners()
        ) throw InvalidUpdatePackage()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.currentSigners() =
        (if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures).orEmpty().toSet()

    private suspend fun HttpClient.readText(url: String, limit: Int, type: ContentType? = null): String =
        prepareGet(url) { if (type != null) accept(type) }.execute { response ->
            check(response.status == HttpStatusCode.OK)
            val bytes = response.bodyAsChannel().readBuffer(limit.toLong() + 1).readByteArray()
            check(bytes.size <= limit)
            bytes.decodeToString()
        }

    private suspend fun HttpClient.downloadApk(
        update: AppUpdate,
        file: File,
        onProgress: (Int) -> Unit,
    ): String = prepareGet(update.downloadUrl(update.apkName)) {
        timeout { requestTimeoutMillis = 5 * 60 * 1000L }
    }.execute { response ->
        check(response.status == HttpStatusCode.OK)
        val digest = MessageDigest.getInstance("SHA-256")
        val body = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var received = 0L
        var shownPercent = 0
        file.outputStream().buffered().use { output ->
            while (true) {
                val count = body.readAvailable(buffer)
                if (count < 0) break
                received += count
                if (received > update.bytes) throw InvalidUpdatePackage()
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                val percent = (received * 100 / update.bytes).toInt()
                if (percent != shownPercent) {
                    shownPercent = percent
                    withContext(Dispatchers.Main) { onProgress(percent) }
                }
            }
        }
        if (received != update.bytes) throw InvalidUpdatePackage()
        digest.digest().toHexString()
    }

    private companion object {
        const val LATEST_RELEASE = "https://git.qqs.tw/api/v1/repos/bobmaster/quantoot_next/releases/latest"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
