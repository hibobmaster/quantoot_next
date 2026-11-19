package com.quanquan.quantoot.platform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val APK_SIZE_LIMIT = 32L * 1024 * 1024 // The Forgejo signing service uses the same limit.
internal const val UPDATE_CHECKSUMS = "SHA256SUMS.txt"

private val androidAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
private val stableVersion = Regex("([0-9]+)\\.([0-9]+)\\.([0-9]+)")
private val checksumLine = Regex("([a-fA-F0-9]{64}) [ *]([^\\s]+)")
private val releaseRepository = Regex.escape(ANDROID_RELEASES.removeSuffix("/releases"))
private val comparisonLink = Regex("""\[[^\n]+]\($releaseRepository/compare/[^\s)]+\)""")
private val commitReference = Regex(""" \(\[[a-f0-9]{7}]\($releaseRepository/commit/[a-f0-9]{40}\)\)$""")
private val manifestComment = Regex("""<!-- quantoot-android-manifest: [a-f0-9]{64} -->""")
private val headingSpacing = Regex("""(?m)^(#{1,6} [^\r\n]+)\r?\n[ \t]*\r?\n""")
private val markdownHeading = Regex("""^#{1,6}\s+""")
private val markdownBullet = Regex("""^[-*+]\s+""")
private val markdownLink = Regex("""(?<!\\)\[([^]\n]+)]\(https?://[^\s)]+\)""")
private val markdownEscape = Regex("""\\([\\\[\]()*_<>`])""")

@Serializable
internal data class AndroidRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
    val body: String? = null,
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
            return AppUpdate(version, abi, apk.size, formatReleaseNotes(body.orEmpty()))
        }
        throw NoCompatibleUpdate()
    }
}

private fun formatReleaseNotes(markdown: String): String =
    markdown.replace(headingSpacing, "$1\n").lineSequence().map(String::trim)
        .filterNot { it == "## 更新日志" || comparisonLink.matches(it) || manifestComment.matches(it) }
        .joinToString("\n") { line ->
            line.replace(commitReference, "")
                .replace(markdownHeading, "")
                .replace(markdownBullet, "• ")
                .replace(markdownLink, "$1")
                .replace(markdownEscape, "$1")
        }.replace(Regex("\n{3,}"), "\n\n").trim()

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
