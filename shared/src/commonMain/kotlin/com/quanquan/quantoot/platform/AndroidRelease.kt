package com.quanquan.quantoot.platform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val APK_SIZE_LIMIT = 32L * 1024 * 1024 // The Forgejo signing service uses the same limit.
internal const val UPDATE_CHECKSUMS = "SHA256SUMS.txt"

private val androidAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
private val stableVersion = Regex("([0-9]+)\\.([0-9]+)\\.([0-9]+)")
private val checksumLine = Regex("([a-fA-F0-9]{64}) [ *]([^\\s]+)")

@Serializable
internal data class AndroidRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
) {
    fun updateFor(installedVersion: String, supportedAbis: List<String>): AppUpdate? {
        if (draft || prerelease) return null
        val version = tag.removePrefix("v")
        val latest = versionNumbers(version) ?: return null
        if (tag != "v$version") return null
        val currentBase = installedVersion.substringBefore('+').substringBefore('-')
        val current = versionNumbers(currentBase) ?: error("Invalid installed version")
        val order = latest.zip(current).firstOrNull { (new, old) -> new != old }
            ?.let { (new, old) -> new.compareTo(old) } ?: 0
        val currentIsPrerelease = '-' in installedVersion.substringBefore('+')
        if (order < 0 || (order == 0 && !currentIsPrerelease)) return null

        if (assets.count { it.name == UPDATE_CHECKSUMS } != 1) throw InvalidUpdatePackage()
        for (abi in supportedAbis) {
            if (abi !in androidAbis) continue
            val name = "QuanToot-$version-$abi.apk"
            val apk = assets.singleOrNull { it.name == name } ?: continue
            if (apk.size !in 1..APK_SIZE_LIMIT) throw InvalidUpdatePackage()
            return AppUpdate(version, abi, apk.size)
        }
        throw NoCompatibleUpdate()
    }
}

@Serializable
internal data class ReleaseAsset(val name: String, val size: Long)

internal fun updateChecksum(manifest: String, apkName: String): String =
    manifest.lineSequence().mapNotNull { checksumLine.matchEntire(it) }
        .filter { it.groupValues[2] == apkName }.singleOrNull()?.groupValues?.get(1)?.lowercase()
        ?: throw InvalidUpdatePackage()

internal fun AppUpdate.isValid(): Boolean =
    versionNumbers(version) != null && abi in androidAbis && bytes in 1..APK_SIZE_LIMIT

private fun versionNumbers(value: String): List<Int>? {
    val match = stableVersion.matchEntire(value) ?: return null
    return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
}
