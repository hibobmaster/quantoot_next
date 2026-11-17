package com.quanquan.quantoot.ui

import androidx.compose.runtime.*
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*

internal data class ThreadResult(
    val root: Status?,
    val context: StatusContext?,
    val error: String? = null,
)

internal class ThreadState(
    val rootId: String,
    private val scope: CoroutineScope,
    private val project: (Status) -> Status? = { it },
    initialRoot: Status? = null,
    private val fetch: suspend () -> ThreadResult,
) {
    private var focal by mutableStateOf(initialRoot)
    private var ancestorMembers by mutableStateOf<List<Status>>(emptyList())
    private var descendantMembers by mutableStateOf<List<Status>>(emptyList())
    private var missingRoot by mutableStateOf(false)
    private var arrivals: List<Status> = emptyList()
    private val parents = mutableMapOf<String, String?>()
    private var active = true
    private var generation = 0
    private var request: Job? = null
    private var scheduled: Job? = null
    private var refreshQueued = false

    var busy by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val root: Status?
        get() = if (missingRoot) null else focal?.let(project)
    val rootUnavailable: Boolean
        get() = missingRoot || (focal != null && root == null)
    val ancestors: List<Status>
        get() = ancestorMembers.mapNotNull(project)
    val descendants: List<Status>
        get() = descendantMembers.mapNotNull(project)
    val rows: List<Status>
        get() = ancestors + listOfNotNull(root) + descendants

    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            generation++
            request?.cancel()
            scheduled?.cancel()
            scheduled = null
            refreshQueued = false
            busy = false
        }
    }

    fun refresh() {
        if (!active) return
        scheduled?.cancel()
        scheduled = null
        if (busy) {
            refreshQueued = true
            return
        }
        val version = ++generation
        busy = true
        error = null
        request = scope.launch {
            try {
                val result = fetch()
                currentCoroutineContext().ensureActive()
                if (version != generation) return@launch
                focal = result.root ?: focal
                missingRoot = result.root == null || project(result.root) == null
                result.context?.let { context ->
                    val seen = mutableSetOf(rootId)
                    val keptAncestors = if (missingRoot) ancestorMembers else emptyList()
                    val keptDescendants = if (missingRoot) descendantMembers else emptyList()
                    ancestorMembers = (context.ancestors + keptAncestors).filter { seen.add(it.id) }
                    val confirmed = (context.descendants + keptDescendants).filter { seen.add(it.id) }
                    arrivals = arrivals.mapNotNull(project).filterNot { it.id in seen }
                    descendantMembers = confirmed + arrivals
                    descendantMembers.forEach { parents[it.id] = it.inReplyToId }
                }
                error = result.error
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == generation) error = e.message ?: "Unable to load replies"
            } finally {
                if (version == generation) {
                    busy = false
                    loaded = true
                    if (refreshQueued && active && scope.isActive) {
                        refreshQueued = false
                        refresh()
                    }
                }
            }
        }
    }

    fun apply(event: StreamEvent) {
        when (event) {
            is StreamEvent.Created -> receive(event.status)
            is StreamEvent.Edited -> if (contains(event.status.original.id)) reconcileSoon()
            is StreamEvent.Deleted -> {
                val relevant = contains(event.id)
                if (event.id == rootId) missingRoot = true
                ancestorMembers = ancestorMembers.filterNot { it.id == event.id }
                descendantMembers = descendantMembers.filterNot { it.id == event.id }
                arrivals = arrivals.filterNot { it.id == event.id }
                if (relevant) reconcileSoon()
            }
            is StreamEvent.Notify -> event.notification.status?.let {
                if (event.notification.type == "mention") receive(it)
                else if (contains(it.original.id)) reconcileSoon()
            }
            is StreamEvent.Reconnected -> reconcileSoon()
        }
    }

    fun includePublished(reply: Status) {
        append(reply)
        reconcileSoon()
    }

    fun depth(id: String): Int {
        val visited = mutableSetOf<String>()
        var parent: String? = id
        var depth = 0
        while (parent != null && parent != rootId && visited.add(parent)) {
            parent = parents[parent]
            depth++
        }
        return if (parent == rootId) depth else 1
    }

    private fun contains(id: String): Boolean =
        id == rootId || id in parents || ancestorMembers.any { it.id == id }

    private fun receive(entry: Status) {
        val status = project(entry) ?: return
        if (status.id == rootId || status.reblog != null) {
            if (contains(status.original.id)) reconcileSoon()
            return
        }
        val parent = status.inReplyToId ?: return
        if (parent == rootId || parent in parents) append(status)
        reconcileSoon()
    }

    private fun append(status: Status) {
        val value = project(status) ?: return
        val parent = value.inReplyToId ?: return
        if (parent != rootId && parent !in parents) return
        if (value.id == rootId || descendantMembers.any { it.id == value.id }) return
        parents[value.id] = value.inReplyToId
        arrivals = arrivals + value
        descendantMembers = descendantMembers + value
    }

    private fun reconcileSoon() {
        if (!active) return
        if (busy) {
            refreshQueued = true
            return
        }
        if (scheduled?.isActive == true) return
        scheduled = scope.launch {
            delay(300)
            scheduled = null
            refresh()
        }
    }
}
