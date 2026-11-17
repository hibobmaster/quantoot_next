package com.quanquan.quantoot.ui

import androidx.compose.runtime.mutableStateMapOf
import com.quanquan.quantoot.model.*
import kotlin.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class StatusProjection {
    private data class FieldStamp(val sequence: Long, val acknowledged: Boolean)

    private data class Record(
        val value: Status,
        val contentSequence: Long,
        val readSequence: Long = 0,
        val eventSequence: Long = 0,
        val fields: Map<StatusAction, FieldStamp> = emptyMap(),
    )

    private enum class Origin { Read, Event, Action }

    private val records = mutableStateMapOf<String, Record>()
    private val deleted = mutableStateMapOf<String, Boolean>()
    private val actionGenerations = mutableMapOf<Pair<String, StatusAction>, Long>()
    private var sequence = 0L

    suspend fun <T> read(load: suspend () -> T, values: (T) -> Iterable<Status>): T {
        val started = ++sequence
        val result = load()
        currentCoroutineContext().ensureActive()
        values(result).forEach { merge(it, started, Origin.Read) }
        return result
    }

    suspend fun action(id: String, action: StatusAction, load: suspend () -> Status): Status {
        val key = id to action
        val generation = (actionGenerations[key] ?: 0) + 1
        actionGenerations[key] = generation
        val started = ++sequence
        val result = load()
        currentCoroutineContext().ensureActive()
        val value = result.original
        require(value.id == id) { "The action returned a different post" }
        if (actionGenerations[key] != generation || deleted[id] == true) return result
        merge(result, started, Origin.Action)
        val current = records[id] ?: return result
        val acknowledged = ++sequence
        records[id] = current.copy(
            value = patch(current.value, value, action),
            fields = current.fields + (action to FieldStamp(acknowledged, true)),
        )
        return result
    }

    fun apply(event: StreamEvent) {
        val received = ++sequence
        when (event) {
            is StreamEvent.Created -> merge(event.status, received, Origin.Event)
            is StreamEvent.Edited -> merge(event.status, received, Origin.Event)
            is StreamEvent.Notify -> event.notification.status?.let { merge(it, received, Origin.Event) }
            is StreamEvent.Deleted -> {
                deleted[event.id] = true
                records.remove(event.id)
            }
            is StreamEvent.Reconnected -> Unit
        }
    }

    fun known(id: String): Status? = records[id]?.value?.let(::project)

    fun project(entry: Status): Status? = project(entry, emptySet())

    private fun project(entry: Status, visiting: Set<String>): Status? {
        if (deleted[entry.id] == true || entry.id in visiting) return null
        val value = records[entry.id]?.value ?: entry
        val path = visiting + entry.id
        val reblog = value.reblog?.let { project(it, path) ?: return null }
        val quote = value.quote?.let {
            val quotedId = it.quotedStatusId ?: it.quotedStatus?.id
            if (deleted[quotedId] == true)
                it.copy(state = "deleted", quotedStatus = null, quotedStatusId = quotedId)
            else it.copy(quotedStatus = it.quotedStatus?.let { status -> project(status, path) })
        }
        return value.copy(reblog = reblog, quote = quote)
    }

    private fun merge(value: Status, stamp: Long, origin: Origin) {
        if (deleted[value.id] == true) return
        value.reblog?.let { merge(it, stamp, origin) }
        value.quote?.quotedStatus?.let { merge(it, stamp, origin) }
        val previous = records[value.id]
        if (previous == null) {
            records[value.id] = Record(
                value,
                stamp,
                readSequence = if (origin == Origin.Read) stamp else 0,
                eventSequence = if (origin == Origin.Event) stamp else 0,
            )
            return
        }
        val currentVersion = version(previous.value)
        val incomingVersion = version(value)
        val olderContent = currentVersion != null &&
            (incomingVersion == null || incomingVersion < currentVersion)
        val currentRequest = stamp >= previous.contentSequence && stamp >= previous.readSequence &&
            stamp >= previous.eventSequence
        val accept = currentRequest && !olderContent
        var merged = if (accept) value else previous.value
        var fields = previous.fields
        for (action in StatusAction.entries) {
            val field = previous.fields[action]
            val acceptField = accept && when (origin) {
                Origin.Read -> stamp >= (field?.sequence ?: 0)
                Origin.Event -> field?.acknowledged != true
                Origin.Action -> false
            }
            if (acceptField) fields = fields + (action to FieldStamp(stamp, false))
            else merged = patch(merged, previous.value, action)
        }
        if (origin == Origin.Event)
            merged = merged.copy(
                favourited = previous.value.favourited,
                reblogged = previous.value.reblogged,
                bookmarked = previous.value.bookmarked,
            )
        records[value.id] = previous.copy(
            value = merged,
            contentSequence = if (accept) stamp else previous.contentSequence,
            readSequence = if (origin == Origin.Read) maxOf(stamp, previous.readSequence) else previous.readSequence,
            eventSequence = if (origin == Origin.Event) maxOf(stamp, previous.eventSequence) else previous.eventSequence,
            fields = fields,
        )
    }

    private fun version(status: Status): Instant? =
        (status.editedAt ?: status.createdAt).takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }

    private fun patch(current: Status, result: Status, action: StatusAction): Status = when (action) {
        StatusAction.Favourite -> current.copy(
            favourited = result.favourited,
            favouritesCount = result.favouritesCount,
        )
        StatusAction.Reblog -> current.copy(reblogged = result.reblogged, reblogsCount = result.reblogsCount)
        StatusAction.Bookmark -> current.copy(bookmarked = result.bookmarked)
    }
}
