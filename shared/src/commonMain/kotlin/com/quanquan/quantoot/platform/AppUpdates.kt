package com.quanquan.quantoot.platform

interface AppUpdates {
    val currentVersion: String

    suspend fun check(): AppUpdate?

    suspend fun download(update: AppUpdate, onProgress: (Int) -> Unit)

    fun canInstall(): Boolean

    fun openInstallSettings()

    suspend fun install(update: AppUpdate)
}

data class AppUpdate(val version: String, val abi: String, val bytes: Long, val releaseNotes: String = "") {
    val releaseUrl: String get() = "$ANDROID_RELEASES/tag/v$version"
    internal val apkName: String get() = "QuanToot-$version-$abi.apk"

    internal fun downloadUrl(name: String): String = "$ANDROID_RELEASES/download/v$version/$name"
}

internal class NoCompatibleUpdate : Exception()

internal class InvalidUpdatePackage : Exception()

internal const val ANDROID_RELEASES = "https://git.qqs.tw/bobmaster/quantoot_next/releases"
