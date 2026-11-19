package com.quanquan.quantoot.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AndroidReleaseTest {
    private val armAbis = listOf("arm64-v8a", "armeabi-v7a")
    private val release = AndroidRelease(
        tag = "v0.6.10",
        draft = false,
        prerelease = false,
        assets = listOf(
            ReleaseAsset("QuanToot-0.6.10-armeabi-v7a.apk", 1500),
            ReleaseAsset("QuanToot-0.6.10-arm64-v8a.apk", 2000),
            ReleaseAsset(UPDATE_CHECKSUMS, 373),
        ),
    )

    @Test fun selectsTheFirstSupportedAbiAndComparesVersionNumbersNumerically() {
        assertEquals(AppUpdate("0.6.10", "arm64-v8a", 2000), release.updateFor("0.6.9", armAbis))
        assertEquals(AppUpdate("0.6.10", "armeabi-v7a", 1500), release.updateFor("0.6.9", armAbis.reversed()))
    }

    @Test fun skipsCurrentOlderDraftAndPrereleaseVersions() {
        assertNull(release.updateFor("0.6.10+local", armAbis))
        assertNull(release.updateFor("0.7.0", armAbis))
        assertNull(release.copy(draft = true).updateFor("0.6.9", armAbis))
        assertNull(release.copy(prerelease = true).updateFor("0.6.9", armAbis))
        assertNull(release.copy(tag = "v0.6.11-rc.1").updateFor("0.6.9", armAbis))
        assertEquals("0.6.10", release.updateFor("0.6.10-rc.1", armAbis)?.version)
    }

    @Test fun requiresTheChecksumManifestAndACompatibleAttachment() {
        assertFailsWith<NoCompatibleUpdate> { release.updateFor("0.6.9", listOf("x86_64")) }
        assertFailsWith<InvalidUpdatePackage> { release.copy(assets = release.assets.dropLast(1)).updateFor("0.6.9", armAbis) }
        val oversized = release.assets.first().copy(size = APK_SIZE_LIMIT + 1)
        assertFailsWith<InvalidUpdatePackage> { release.copy(assets = listOf(oversized, release.assets.last())).updateFor("0.6.9", armAbis) }
    }

    @Test fun checksumMustMatchExactlyOneEntryForTheSelectedFile() {
        val digest = "abc01234".repeat(8)
        val name = "QuanToot-0.6.10-arm64-v8a.apk"
        val line = "$digest  $name"
        assertEquals(digest, updateChecksum("${"0".repeat(64)} *other.apk\r\n$line\r\n", name))
        assertFailsWith<InvalidUpdatePackage> { updateChecksum("$line\n$line\n", name) }
        assertFailsWith<InvalidUpdatePackage> { updateChecksum("$digest  other/$name\n", name) }
        assertFailsWith<InvalidUpdatePackage> { updateChecksum("bad-digest  $name\n", name) }
    }
}
