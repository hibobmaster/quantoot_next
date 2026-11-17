package com.quanquan.quantoot.ui

import androidx.compose.runtime.*
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.KeyValueStore
import com.quanquan.quantoot.platform.sha256
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class FeedSnapshot<T>(
    val items: List<T> = emptyList(),
    val index: Int = 0,
    val offset: Int = 0,
    val nextCursor: String? = null,
    val continuationKnown: Boolean = false,
)

internal fun feedCacheKey(instance: String, accountKey: String?, timeline: String): String {
    val identity =
        listOf(instance.lowercase(), accountKey ?: "guest", timeline).joinToString("") {
            "${it.length}:$it"
        }
    return "feed_cache_v1_" +
        sha256(identity.encodeToByteArray()).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
}

internal class FeedCache<T>(
    private val preferences: KeyValueStore,
    private val key: String,
    serializer: KSerializer<T>,
) {
    private val format = Json { ignoreUnknownKeys = true }
    private val codec = FeedSnapshot.serializer(serializer)

    suspend fun read(): FeedSnapshot<T>? =
        try {
            preferences
                .read(key)
                ?.takeIf { it.length <= 512 * 1024 && it.encodeToByteArray().size <= 512 * 1024 }
                ?.let { format.decodeFromString(codec, it) }
                ?.takeIf {
                    it.items.size <= 100 &&
                        it.index >= 0 &&
                        it.offset >= 0 &&
                        (it.nextCursor == null ||
                            (it.nextCursor.length <= 2048 &&
                                it.nextCursor.none { c -> c.code < 32 || c.code == 127 }))
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    suspend fun write(snapshot: FeedSnapshot<T>) {
        var bounded = snapshot.items.take(100)
        var encoded: String
        while (true) {
            encoded =
                format.encodeToString(
                    codec,
                    FeedSnapshot(
                        bounded,
                        snapshot.index.coerceIn(0, (bounded.size - 1).coerceAtLeast(0)),
                        snapshot.offset.coerceAtLeast(0),
                        snapshot.nextCursor,
                        snapshot.continuationKnown && bounded.size == snapshot.items.size,
                    ),
                )
            if (encoded.encodeToByteArray().size <= 512 * 1024) break
            if (bounded.isEmpty()) return
            bounded = bounded.dropLast(1)
        }
        try {
            preferences.write(key, encoded)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }
}

internal class FeedPager<T>(
    private val scope: CoroutineScope,
    private val id: (T) -> String,
    private val project: (T) -> T? = { it },
    private val fetch: suspend (String?) -> Page<T>,
) {
    var rows by mutableStateOf<List<T>>(emptyList())
        private set
    var pending by mutableStateOf<List<T>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var paging by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var failedMore by mutableStateOf(false)
        private set
    var exhausted by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set

    private var cursor by mutableStateOf<String?>(null)
    private var continuationKnown by mutableStateOf(false)
    private var generation = 0
    private var request: Job? = null
    private var creations: List<T> = emptyList()
    private val removed = mutableSetOf<String>()

    private fun projected(items: List<T>): List<T> =
        items.mapNotNull(project).filterNot { id(it) in removed }.distinctBy(id)

    fun restore(snapshot: FeedSnapshot<T>? = null) {
        request?.cancel()
        generation++
        if (ready) {
            pending = emptyList()
            creations = emptyList()
        }
        rows = projected((if (ready) emptyList() else rows) + snapshot?.items.orEmpty())
        pending = projected(pending).filterNot { item -> rows.any { id(it) == id(item) } }
        cursor = snapshot?.nextCursor
        continuationKnown = snapshot?.continuationKnown == true
        exhausted = continuationKnown && cursor == null
        busy = false
        paging = false
        ready = true
    }

    fun snapshot(index: Int = 0, offset: Int = 0): FeedSnapshot<T> =
        FeedSnapshot(projected(rows), index, offset, cursor, continuationKnown)

    fun load(more: Boolean = false, defer: Boolean = false) {
        if (!ready || (more && (busy || exhausted || !continuationKnown))) return
        if (!more) request?.cancel()
        val version = ++generation
        val maxId = if (more) cursor else null
        val preserveTail = defer && continuationKnown && rows.isNotEmpty()
        busy = true
        paging = more
        error = null
        failedMore = more
        request = scope.launch {
            try {
                val result = fetch(maxId)
                currentCoroutineContext().ensureActive()
                if (version != generation) return@launch
                val page = projected(result.items)
                val confirmed = result.items.map(id).toSet()
                creations = projected(creations).filterNot { id(it) in confirmed }
                prune()
                val existing = rows.map(id).toSet()
                val updates = page.associateBy(id)
                if (more) {
                    rows = rows.map { updates[id(it)] ?: it } + page.filterNot { id(it) in existing }
                    exhausted = result.nextCursor == null || result.nextCursor == maxId
                    cursor = result.nextCursor
                    continuationKnown = true
                } else if (preserveTail) {
                    val firstKnown = page.indexOfFirst { id(it) in existing }
                    val arrivals = if (firstKnown < 0) page else page.take(firstKnown)
                    pending = projected(creations + arrivals + pending)
                        .filterNot { id(it) in existing }.take(100)
                    rows = rows.map { updates[id(it)] ?: it }
                } else {
                    rows = projected(creations + pending + page)
                    pending = emptyList()
                    cursor = result.nextCursor
                    continuationKnown = true
                    exhausted = result.nextCursor == null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == generation) error = e.message ?: "Request failed"
            } finally {
                if (version == generation) {
                    busy = false
                    paging = false
                }
            }
        }
    }

    fun created(item: T, defer: Boolean) {
        val value = project(item)?.takeUnless { id(it) in removed } ?: return
        val itemId = id(value)
        if (rows.any { id(it) == itemId }) {
            rows = rows.map { if (id(it) == itemId) value else it }
            return
        }
        if (pending.none { id(it) == itemId })
            creations = projected(listOf(value) + creations).take(100)
        pending = projected(listOf(value) + pending).take(100)
        if (!defer) reveal()
    }

    fun reveal() {
        rows = projected(pending + rows)
        pending = emptyList()
    }

    fun prune() {
        rows = projected(rows)
        pending = projected(pending)
        creations = projected(creations)
    }

    fun remove(predicate: (T) -> Boolean) {
        removed += (rows + pending + creations).filter(predicate).map(id)
        prune()
    }
}

internal fun groupFeedNotifications(items: List<Notification>): List<List<Notification>> {
    fun compareIds(a: String, b: String): Int {
        if (a.any { !it.isDigit() } || b.any { !it.isDigit() }) return 0
        val left = a.trimStart('0')
        val right = b.trimStart('0')
        return left.length.compareTo(right.length).takeIf { it != 0 } ?: left.compareTo(right)
    }
    val sorted = items.sortedWith { a, b ->
        b.createdAt.compareTo(a.createdAt).takeIf { it != 0 } ?: compareIds(b.id, a.id)
    }
    val groups = mutableListOf<MutableList<Notification>>()
    val interactions = mutableMapOf<Pair<String, String>, MutableList<Notification>>()
    for (item in sorted) {
        if (item.type in setOf("favourite", "reblog") && item.status != null) {
            val key = item.type to item.status.id
            val group = interactions[key]
            if (group == null) {
                val fresh = mutableListOf(item)
                interactions[key] = fresh
                groups.add(fresh)
            } else if (group.none { it.account.id == item.account.id }) group.add(item)
        } else groups.add(mutableListOf(item))
    }
    return groups
}
