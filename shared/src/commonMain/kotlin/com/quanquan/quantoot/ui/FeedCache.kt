package com.quanquan.quantoot.ui

import androidx.compose.runtime.*
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.KeyValueStore
import com.quanquan.quantoot.platform.sha256
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
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
    val leadingOffset: Int? = null,
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
    internal data class Value<T>(val snapshot: FeedSnapshot<T>, val sourceSize: Int)

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
                        (it.leadingOffset == null || it.leadingOffset >= 0) &&
                        (it.nextCursor == null ||
                            (it.nextCursor.length <= 2048 &&
                                it.nextCursor.none { c -> c.code < 32 || c.code == 127 }))
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    fun prepare(snapshot: FeedSnapshot<T>, previous: Value<T>? = null): Value<T>? {
        if (snapshot.nextCursor != null &&
            (snapshot.nextCursor.length > 2048 || snapshot.nextCursor.any { it.code < 32 || it.code == 127 }))
            return null
        fun normalized(items: List<T>): Value<T> {
            val index = snapshot.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            return Value(
                FeedSnapshot(
                    items,
                    if (snapshot.leadingOffset != null) 0 else index,
                    if (snapshot.leadingOffset != null || index != snapshot.index || items.isEmpty()) 0
                    else snapshot.offset.coerceAtLeast(0),
                    snapshot.nextCursor,
                    snapshot.continuationKnown && items.size == snapshot.items.size,
                    snapshot.leadingOffset?.coerceAtLeast(0),
                ),
                snapshot.items.size,
            )
        }
        if (previous != null && previous.sourceSize == snapshot.items.size &&
            previous.snapshot.nextCursor == snapshot.nextCursor &&
            (previous.snapshot.items.isNotEmpty() || snapshot.items.isEmpty()) &&
            previous.snapshot.items == snapshot.items.take(previous.snapshot.items.size))
            return normalized(previous.snapshot.items)
        var bounded = snapshot.items.take(100)
        while (true) {
            val largestMetadata = snapshot.copy(
                items = bounded,
                index = Int.MAX_VALUE,
                offset = Int.MAX_VALUE,
                leadingOffset = Int.MAX_VALUE,
                continuationKnown = true,
            )
            val encoded = format.encodeToString(codec, largestMetadata)
            if (encoded.encodeToByteArray().size <= 512 * 1024) return normalized(bounded)
            if (bounded.isEmpty()) return null
            bounded = bounded.dropLast(1)
        }
    }

    suspend fun write(snapshot: FeedSnapshot<T>) {
        prepare(snapshot)?.let { write(it) }
    }

    suspend fun write(value: Value<T>): Boolean =
        try {
            preferences.write(key, format.encodeToString(codec, value.snapshot))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
}

internal class FeedCacheSession<T>(
    private val preferences: KeyValueStore,
    private val serializer: KSerializer<T>,
    private val scope: CoroutineScope,
) {
    private inner class Entry(val key: String) {
        val cache = FeedCache(preferences, key, serializer)
        var latest: FeedCache.Value<T>? = null
        var revision = 0L
        var persistedRevision = 0L
        var attachment: Long? = null
        var writer: Job? = null
        val changes = Channel<Unit>(Channel.CONFLATED)
    }

    private val entries = mutableMapOf<String, Entry>()
    private var epoch = 0L
    private var closed = false

    inner class Attachment internal constructor(key: String, private val epoch: Long) {
        private val entry = entries.getValue(key)
        private var closed = false
        val isCurrent: Boolean
            get() = !closed && !this@FeedCacheSession.closed &&
                entries[entry.key] === entry && entry.attachment == epoch

        suspend fun read(): FeedSnapshot<T>? {
            if (!isCurrent) return null
            entry.latest?.let { return it.snapshot }
            val saved = entry.cache.read()
            currentCoroutineContext().ensureActive()
            if (!isCurrent) return null
            if (entry.latest == null && saved != null) entry.latest = entry.cache.prepare(saved)
            return entry.latest?.snapshot
        }

        fun checkpoint(snapshot: FeedSnapshot<T>) {
            if (!isCurrent) return
            if (entry.latest?.snapshot != snapshot) {
                val value = entry.cache.prepare(snapshot, entry.latest) ?: return
                if (entry.latest != value) {
                    entry.latest = value
                    entry.revision++
                }
            }
            if (entry.revision != entry.persistedRevision) {
                entry.changes.trySend(Unit)
                if (entry.writer == null) entry.writer = scope.launch { persist(entry) }
            }
        }

        fun close(snapshot: FeedSnapshot<T>? = null) {
            if (closed) return
            if (isCurrent) {
                snapshot?.let(::checkpoint)
                entry.attachment = null
                release(entry)
            }
            closed = true
        }
    }

    fun attach(key: String): Attachment {
        check(!closed)
        val entry = entries.getOrPut(key) { Entry(key) }
        entry.attachment = ++epoch
        return Attachment(key, epoch)
    }

    private suspend fun persist(entry: Entry) {
        try {
            while (true) {
                while (withTimeoutOrNull(400) { entry.changes.receive() } != null) {}
                val revision = entry.revision
                val latest = entry.latest ?: return
                if (entry.cache.write(latest)) entry.persistedRevision = revision
                if (revision == entry.revision) return
            }
        } finally {
            entry.writer = null
            release(entry)
        }
    }

    private fun release(entry: Entry) {
        if (entry.attachment == null && entry.writer == null && entry.revision == entry.persistedRevision &&
            entries[entry.key] === entry)
            entries.remove(entry.key)
    }

    fun close() {
        if (closed) return
        closed = true
        val writers = entries.values.mapNotNull { it.writer }
        entries.clear()
        writers.forEach { it.cancel() }
    }
}

internal class FeedPager<T>(
    val scope: CoroutineScope,
    private val id: (T) -> String,
    private val project: (T) -> T? = { it },
    private val onClose: (FeedPager<T>) -> Unit = {},
    private val beforeReplace: () -> Unit = {},
    private val fetch: suspend (String?) -> Page<T>,
) {
    var rows by mutableStateOf<List<T>>(emptyList())
        private set
    var pending by mutableStateOf<List<T>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    val refreshing: Boolean get() = busy && !paging && rows.isNotEmpty()
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
    private var closed = false

    private fun projected(items: List<T>): List<T> =
        items.mapNotNull(project).filterNot { id(it) in removed }.distinctBy(id)

    fun restore(snapshot: FeedSnapshot<T>? = null) {
        if (closed) return
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
        if (closed || !ready || (more && (busy || exhausted || !continuationKnown))) return
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
                    beforeReplace()
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
        if (closed) return
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
        if (closed) return
        if (pending.isNotEmpty()) beforeReplace()
        rows = projected(pending + rows)
        pending = emptyList()
    }

    fun prune() {
        if (closed) return
        rows = projected(rows)
        pending = projected(pending)
        creations = projected(creations)
    }

    fun remove(predicate: (T) -> Boolean) {
        if (closed) return
        removed += (rows + pending + creations).filter(predicate).map(id)
        prune()
    }

    fun cancelLoad() {
        request?.cancel()
        request = null
        generation++
        busy = false
        paging = false
    }

    fun close() {
        if (closed) return
        closed = true
        cancelLoad()
        onClose(this)
    }
}

internal suspend fun <T> FeedPager<T>.revealWhenAtTop(isAtTop: () -> Boolean) {
    // The queue can clear and fill again within one snapshot notification.
    snapshotFlow { pending.takeIf { it.isNotEmpty() && isAtTop() }.orEmpty() }.collect { arrivals ->
        if (arrivals.isNotEmpty()) reveal()
    }
}

internal fun <T> FeedPager<T>.snapshot(
    position: FeedPosition,
    emptyPosition: FeedPosition,
    id: (T) -> String,
): FeedSnapshot<T> {
    val snapshot = snapshot()
    val normalized = when (position) {
        is FeedPosition.Leading -> position
        is FeedPosition.Status -> {
            val index = position.index.coerceIn(0, rows.lastIndex.coerceAtLeast(0))
            FeedSnapshot(rows, index, if (index == position.index) position.offset else 0)
                .restoredPosition(snapshot.items, id, emptyPosition is FeedPosition.Leading)
        }
    }
    return snapshot.atPosition(normalized)
}

internal fun FeedPager<Status>.apply(
    event: StreamEvent,
    source: StreamSource?,
    defer: Boolean,
    active: Boolean,
) {
    when (event) {
        is StreamEvent.Created -> when (event.source) {
            StreamSource.LocalWrite -> if (active) load(defer = defer)
            source -> created(event.status, defer = defer)
            else -> Unit
        }
        is StreamEvent.Edited, is StreamEvent.Deleted -> prune()
        is StreamEvent.Reconnected ->
            if (active && (source == null || event.source == source)) load(defer = defer)
        is StreamEvent.Notify -> Unit
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
