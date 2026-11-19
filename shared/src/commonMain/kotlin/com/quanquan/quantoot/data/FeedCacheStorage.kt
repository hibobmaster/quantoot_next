package com.quanquan.quantoot.data

import com.quanquan.quantoot.platform.AppPreferences
import com.quanquan.quantoot.platform.KeyValueStore
import com.quanquan.quantoot.platform.sha256
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FeedCacheStorage(
    private val preferences: AppPreferences,
    private val isSavedAccount: (String) -> Boolean,
) {
    private val mutex = Mutex()
    private val epochs = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var migrated = false

    fun forSession(instance: String, accountKey: String?): KeyValueStore {
        val prefix = cachePrefix(instance, accountKey)
        val epoch = epochs.value[accountKey] ?: 0L
        return object : KeyValueStore {
            private fun isCurrent() = accountKey == null ||
                (isSavedAccount(accountKey) && (epochs.value[accountKey] ?: 0L) == epoch)

            override suspend fun read(key: String): String? = mutex.withLock {
                if (!isCurrent()) return@withLock null
                migrateLegacy()
                preferences.read(prefix + cacheDigest(key))
            }

            override suspend fun write(key: String, value: String?) = mutex.withLock {
                if (!isCurrent()) return@withLock
                migrateLegacy()
                preferences.write(prefix + cacheDigest(key), value)
            }
        }
    }

    suspend fun removeAccount(instance: String, accountKey: String, removeSession: suspend () -> Unit) {
        mutex.withLock {
            migrateLegacy()
            preferences.removeByPrefix(cachePrefix(instance, accountKey))
            removeSession()
            epochs.value = epochs.value + (accountKey to ((epochs.value[accountKey] ?: 0L) + 1))
        }
    }

    private suspend fun migrateLegacy() {
        if (migrated) return
        if (preferences.read(MIGRATION_KEY) != "2") {
            preferences.removeByPrefix("feed_cache_v1_")
            preferences.write(MIGRATION_KEY, "2")
        }
        migrated = true
    }
}

private const val MIGRATION_KEY = "feed_cache_schema_version"

private fun cachePrefix(instance: String, accountKey: String?): String {
    val owner = if (accountKey == null) "guest" else "account:$accountKey"
    return "feed_cache_v2_${cacheDigest(instance.lowercase())}_${cacheDigest(owner)}_"
}

private fun cacheDigest(value: String): String =
    sha256(value.encodeToByteArray()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
