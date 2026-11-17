package com.quanquan.quantoot.data

import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.PickedMedia
import com.quanquan.quantoot.platform.createHttpClient
import io.ktor.client.*
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlin.time.TimeSource
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class MastodonApi(
    val instance: String,
    accessToken: String? = null,
    client: HttpClient = createHttpClient(),
) {
    private val base = "https://${normalizeInstance(instance)}"
    private val token =
        accessToken
            ?.takeIf { it.isNotBlank() }
            ?.also {
                require(it.none { c -> c.code < 32 || c.code == 127 }) { "Invalid access token" }
            }
    private val sourceHttp = client
    private val http = client.config {
        followRedirects = false
        expectSuccess = false
    }
    private val streamLifetime = SupervisorJob()
    private val filterMutex = Mutex()
    private val knownFilters = mutableMapOf<String, ContentFilter>()
    private var cachedServer: ServerInfo? = null

    private fun requireAuth() {
        if (token == null) throw ApiException(401, "Sign in to use this feature")
    }

    private fun segment(value: String): String {
        require(value.isNotBlank()) { "Missing identifier" }
        return value.encodeURLPathPart()
    }

    private fun page(maxId: String?, sinceId: String? = null, limit: Int = 20) = Parameters.build {
        append("limit", limit.coerceIn(1, 40).toString())
        maxId?.takeIf { it.isNotBlank() }?.let { append("max_id", it) }
        sinceId?.takeIf { it.isNotBlank() }?.let { append("since_id", it) }
    }

    private suspend fun request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        auth: Boolean = false,
        query: Parameters = Parameters.Empty,
        body: Any? = null,
        timeout: Long = 20_000,
        idempotencyKey: String? = null,
        allowEmpty: Boolean = false,
    ): Reply {
        if (auth) requireAuth()
        return try {
            withTimeout(timeout) {
                http
                    .prepareRequest(base + path) {
                        this.method = method
                        timeout {
                            requestTimeoutMillis = timeout
                            socketTimeoutMillis = timeout
                        }
                        url.parameters.appendAll(query)
                        header(HttpHeaders.Accept, ContentType.Application.Json)
                        token?.let { bearerAuth(it) }
                        idempotencyKey?.let { header("Idempotency-Key", it) }
                        when (body) {
                            is JsonElement -> {
                                contentType(ContentType.Application.Json)
                                setBody(body.toString())
                            }
                            null -> Unit
                            else -> setBody(body)
                        }
                    }
                    .execute { response ->
                        if ((response.contentLength() ?: 0L) > MAX_RESPONSE_BYTES)
                            throw ApiException(-1, "Response exceeds the 8 MiB limit")
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(8192)
                        val chunks = mutableListOf<ByteArray>()
                        var total = 0
                        while (true) {
                            val read =
                                channel.readAvailable(
                                    buffer,
                                    0,
                                    minOf(buffer.size, MAX_RESPONSE_BYTES + 1 - total),
                                )
                            if (read == -1) break
                            if (read == 0) {
                                yield()
                                continue
                            }
                            total += read
                            if (total > MAX_RESPONSE_BYTES)
                                throw ApiException(-1, "Response exceeds the 8 MiB limit")
                            chunks += buffer.copyOf(read)
                        }
                        val bytes = ByteArray(total)
                        var offset = 0
                        chunks.forEach {
                            it.copyInto(bytes, offset)
                            offset += it.size
                        }
                        val text = bytes.decodeToString()
                        val code = response.status.value
                        if (code !in 200..299) {
                            throw ApiException(
                                code,
                                when (code) {
                                    401 -> "Your session has expired. Sign in again"
                                    403 -> "The server refused this request"
                                    404 -> "The requested resource or feature is unavailable"
                                    422 -> "The server could not accept these values"
                                    429 -> "Too many requests. Try again shortly"
                                    in 300..399 -> "The server redirected this request"
                                    else -> "The server returned HTTP $code"
                                },
                            )
                        }
                        Reply(
                            code,
                            if (text.isBlank() && allowEmpty) JsonNull else boundedJson(text),
                            response.headers,
                        )
                    }
            }
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw ApiException(-1, "The request timed out")
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error
        } catch (_: Exception) {
            throw ApiException(-1, "Unable to contact the server")
        }
    }

    private data class Reply(val code: Int, val value: JsonElement, val headers: Headers)

    private suspend inline fun <reified T> get(
        path: String,
        auth: Boolean = false,
        query: Parameters = Parameters.Empty,
    ): T = decodeApi(request(path, auth = auth, query = query).value)

    private suspend inline fun <reified T> getPaged(
        path: String,
        auth: Boolean = false,
        query: Parameters = Parameters.Empty,
    ): Page<T> {
        val reply = request(path, auth = auth, query = query)
        val items = decodeApi<List<T>>(reply.value)
        return Page(
            items,
            if (items.isEmpty()) null
            else nextCursor(reply.headers.getAll(HttpHeaders.Link).orEmpty(), path, query),
        )
    }

    private fun nextCursor(headers: List<String>, path: String, query: Parameters): String? {
        val links = headers.flatMap { it.split(Regex(",(?=\\s*<)")) }
        val next =
            links
                .mapNotNull { link ->
                    val match =
                        Regex("^\\s*<([^<>]+)>\\s*;\\s*rel=\"([^\"]+)\"\\s*$").matchEntire(link)
                            ?: return@mapNotNull null
                    match.groupValues[1].takeIf { "next" in match.groupValues[2].split(' ') }
                }
                .singleOrNull() ?: return null
        if (
            next.length > 16384 ||
                next.any { it.isWhitespace() || it.code < 32 } ||
                '\\' in next ||
                Regex("%(?![0-9A-Fa-f]{2})").containsMatchIn(next)
        )
            return null
        val url = runCatching { Url(next) }.getOrNull() ?: return null
        val origin = Url(base)
        if (
            url.protocol != origin.protocol ||
                url.host != origin.host ||
                url.port != origin.port ||
                url.user != null ||
                url.password != null ||
                url.fragment.isNotEmpty() ||
                url.encodedPath != path
        )
            return null
        if (url.parameters.entries().any { it.value.size != 1 }) return null
        val expected =
            query.entries().filter { it.key != "max_id" }.associate { it.key to it.value }
        val actual =
            url.parameters.entries().filter { it.key != "max_id" }.associate { it.key to it.value }
        if (actual != expected) return null
        return url.parameters["max_id"]?.takeIf { value ->
            value.isNotBlank() &&
                value.length <= 2048 &&
                value.none { it.code < 32 || it.code == 127 }
        }
    }

    private suspend inline fun <reified T> send(
        path: String,
        method: HttpMethod = HttpMethod.Post,
        body: Any? = null,
    ): T = decodeApi(request(path, method, auth = true, body = body).value)

    suspend fun timeline(
        source: Timeline,
        maxId: String? = null,
        sinceId: String? = null,
        limit: Int = 20,
    ): Page<Status> {
        val query = ParametersBuilder().apply { appendAll(page(maxId, sinceId, limit)) }
        val path =
            when (source) {
                Timeline.Home -> "/api/v1/timelines/home"
                is Timeline.Public ->
                    "/api/v1/timelines/public"
                        .also { if (source.local) query.append("local", "true") }
                is Timeline.Hashtag ->
                    "/api/v1/timelines/tag/${segment(source.tag.removePrefix("#"))}"
                is Timeline.AccountPosts ->
                    "/api/v1/accounts/${segment(source.accountId)}/statuses"
                        .also {
                            if (source.onlyMedia) query.append("only_media", "true")
                            if (source.pinned) query.append("pinned", "true")
                            if (source.excludeReplies) query.append("exclude_replies", "true")
                            if (source.excludeReblogs) query.append("exclude_reblogs", "true")
                        }
                Timeline.Favourites -> "/api/v1/favourites"
                Timeline.Bookmarks -> "/api/v1/bookmarks"
            }
        return if (source == Timeline.Favourites || source == Timeline.Bookmarks) {
            getPaged(path, auth = true, query = query.build())
        } else {
            val items: List<Status> = get(path, source == Timeline.Home, query.build())
            Page(items, items.lastOrNull()?.id)
        }
    }

    suspend fun status(id: String): Status = get("/api/v1/statuses/${segment(id)}")

    suspend fun context(id: String): StatusContext = get("/api/v1/statuses/${segment(id)}/context")

    suspend fun trends(): List<Status> = get("/api/v1/trends/statuses")

    suspend fun trendingTags(): List<Tag> = get("/api/v1/trends/tags")

    suspend fun search(query: String, type: String? = null, offset: Int = 0): SearchResults =
        get(
            "/api/v2/search",
            query =
                Parameters.build {
                    append("q", query)
                    append("offset", offset.coerceAtLeast(0).toString())
                    append("limit", "20")
                    append("resolve", (token != null).toString())
                    type?.let {
                        require(it in setOf("accounts", "statuses", "hashtags"))
                        append("type", it)
                    }
                },
        )

    suspend fun account(id: String): Account = get("/api/v1/accounts/${segment(id)}")

    suspend fun lookupAccount(acct: String): Account =
        get("/api/v1/accounts/lookup", query = parametersOf("acct", acct))

    suspend fun verifyCredentials(): Account =
        get("/api/v1/accounts/verify_credentials", auth = true)

    suspend fun relationship(id: String): Relationship =
        get<List<Relationship>>("/api/v1/accounts/relationships", true, parametersOf("id[]", id))
            .firstOrNull { it.id == id } ?: throw ApiException(-1, "Missing account relationship")

    suspend fun relationshipAction(id: String, action: String): Relationship {
        require(action in setOf("follow", "unfollow", "mute", "unmute", "block", "unblock")) {
            "Invalid relationship action"
        }
        return send("/api/v1/accounts/${segment(id)}/$action")
    }

    suspend fun followRequest(id: String, approve: Boolean) {
        request(
            "/api/v1/follow_requests/${segment(id)}/${if (approve) "authorize" else "reject"}",
            HttpMethod.Post,
            true,
            allowEmpty = true,
        )
    }

    suspend fun report(accountId: String, comment: String, statusIds: List<String> = emptyList()) {
        request(
            "/api/v1/reports",
            HttpMethod.Post,
            true,
            body =
                buildJsonObject {
                    put("account_id", accountId)
                    put("comment", comment.trim())
                    put("category", "other")
                    put("forward", false)
                    put("status_ids", JsonArray(statusIds.map(::JsonPrimitive)))
                },
        )
    }

    suspend fun people(id: String, following: Boolean, maxId: String? = null): Page<Account> =
        getPaged(
            "/api/v1/accounts/${segment(id)}/${if (following) "following" else "followers"}",
            query = page(maxId),
        )

    suspend fun notifications(maxId: String? = null): List<Notification> =
        get("/api/v1/notifications", true, page(maxId))

    suspend fun unreadCount(): Int =
        (get<JsonObject>("/api/v2/notifications/unread_count", true)["count"] as? JsonPrimitive)
            ?.content
            ?.toIntOrNull()
            ?.coerceAtLeast(0) ?: 0

    suspend fun markRead(timeline: String, lastReadId: String) {
        require(timeline in setOf("home", "notifications"))
        request(
            "/api/v1/markers",
            HttpMethod.Post,
            true,
            body = FormDataContent(parametersOf("$timeline[last_read_id]", lastReadId)),
        )
    }

    suspend fun setStatusAction(id: String, action: StatusAction, enabled: Boolean): Status =
        send("/api/v1/statuses/${segment(id)}/${if (enabled) "" else "un"}${action.endpoint}")

    suspend fun vote(id: String, choices: List<Int>): Poll {
        require(choices.isNotEmpty() && choices.all { it >= 0 })
        return send(
            "/api/v1/polls/${segment(id)}/votes",
            body =
                FormDataContent(
                    Parameters.build { choices.forEach { append("choices[]", it.toString()) } }
                ),
        )
    }

    suspend fun publish(
        draft: StatusDraft,
        editId: String? = null,
        idempotencyKey: String = randomOperationId(),
    ): Status {
        require(
            idempotencyKey.isNotBlank() &&
                idempotencyKey.length <= 200 &&
                idempotencyKey.none { it.code < 32 || it.code == 127 }
        )
        val body =
            if (editId != null) {
                buildJsonObject {
                    put("status", draft.text)
                    put("visibility", draft.visibility)
                    put("spoiler_text", draft.spoilerText)
                    put("sensitive", draft.sensitive)
                    draft.language?.let { put("language", it) }
                    draft.inReplyToId?.let { put("in_reply_to_id", it) }
                    put("media_ids", JsonArray(draft.mediaIds.map(::JsonPrimitive)))
                    draft.quotedStatusId?.let { put("quoted_status_id", it) }
                    draft.quotePolicy?.let { put("quote_approval_policy", it) }
                    draft.poll?.let { poll ->
                        put(
                            "poll",
                            buildJsonObject {
                                put("options", JsonArray(poll.options.map(::JsonPrimitive)))
                                put("expires_in", poll.expiresIn)
                                put("multiple", poll.multiple)
                                put("hide_totals", poll.hideTotals)
                            },
                        )
                    }
                }
            } else {
                FormDataContent(
                    Parameters.build {
                        append("status", draft.text)
                        append("visibility", draft.visibility)
                        append("spoiler_text", draft.spoilerText)
                        append("sensitive", draft.sensitive.toString())
                        draft.language?.let { append("language", it) }
                        draft.inReplyToId?.let { append("in_reply_to_id", it) }
                        draft.mediaIds.forEach { append("media_ids[]", it) }
                        draft.quotedStatusId?.let { append("quoted_status_id", it) }
                        draft.quotePolicy?.let { append("quote_approval_policy", it) }
                        draft.poll?.let { poll ->
                            poll.options.forEach { append("poll[options][]", it) }
                            append("poll[expires_in]", poll.expiresIn.toString())
                            append("poll[multiple]", poll.multiple.toString())
                            append("poll[hide_totals]", poll.hideTotals.toString())
                        }
                    }
                )
            }
        return decodeApi(
            request(
                    "/api/v1/statuses" + (editId?.let { "/${segment(it)}" } ?: ""),
                    if (editId == null) HttpMethod.Post else HttpMethod.Put,
                    true,
                    body = body,
                    idempotencyKey = idempotencyKey,
                )
                .value
        )
    }

    suspend fun deleteStatus(id: String) {
        request("/api/v1/statuses/${segment(id)}", HttpMethod.Delete, true, allowEmpty = true)
    }

    private fun mediaPart(builder: FormBuilder, key: String, media: PickedMedia) {
        require(media.bytes.size <= 30 * 1024 * 1024) { "Media exceeds the 30 MiB upload limit" }
        val name = media.name.replace(Regex("[\\r\\n\"\\\\]"), "_").take(200).ifBlank { "upload" }
        val type = runCatching {
            ContentType.parse(media.mimeType)
        }
            .getOrDefault(ContentType.Application.OctetStream)
        builder.append(
            key,
            media.bytes,
            Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                append(HttpHeaders.ContentType, type.toString())
            },
        )
    }

    suspend fun upload(media: PickedMedia, description: String = ""): MediaAttachment {
        requireAuth()
        return try {
            withTimeout(120_000) {
                val body =
                    MultiPartFormDataContent(
                        formData {
                            mediaPart(this, "file", media)
                            append("description", description)
                        }
                    )
                val reply =
                    request("/api/v2/media", HttpMethod.Post, true, body = body, timeout = 120_000)
                var attachment = decodeApi<MediaAttachment>(reply.value)
                if (reply.code == 202 || attachment.url.isNullOrBlank()) {
                    require(attachment.id.isNotBlank()) { "Missing uploaded media identifier" }
                    while (true) {
                        delay(1000)
                        val polled =
                            request(
                                "/api/v1/media/${segment(attachment.id)}",
                                auth = true,
                                allowEmpty = true,
                            )
                        if (polled.code == 206 || polled.value == JsonNull) continue
                        attachment = decodeApi(polled.value)
                        if (!attachment.url.isNullOrBlank()) break
                    }
                }
                attachment
            }
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw ApiException(-1, "Media processing timed out")
        }
    }

    suspend fun updateMedia(id: String, description: String): MediaAttachment =
        send(
            "/api/v1/media/${segment(id)}",
            HttpMethod.Put,
            FormDataContent(parametersOf("description", description)),
        )

    suspend fun emojis(): List<CustomEmoji> = get("/api/v1/custom_emojis")

    suspend fun updateProfile(update: ProfileUpdate): Account {
        val body =
            MultiPartFormDataContent(
                formData {
                    append("display_name", update.displayName)
                    append("note", update.note)
                    append("locked", update.locked.toString())
                    update.fields
                        .ifEmpty { listOf(ProfileField()) }
                        .forEachIndexed { index, field ->
                            append("fields_attributes[$index][name]", field.name)
                            append("fields_attributes[$index][value]", field.value)
                        }
                    update.avatar?.let { mediaPart(this, "avatar", it) }
                    update.header?.let { mediaPart(this, "header", it) }
                }
            )
        return decodeApi(
            request(
                    "/api/v1/accounts/update_credentials",
                    HttpMethod.Patch,
                    true,
                    body = body,
                    timeout = 120_000,
                )
                .value
        )
    }

    suspend fun deleteProfileImage(avatar: Boolean): Account =
        send("/api/v1/profile/${if (avatar) "avatar" else "header"}", HttpMethod.Delete)

    suspend fun accountSettings(): AccountSettings {
        val account = verifyCredentials()
        val preferences = get<JsonObject>("/api/v1/preferences", true)
        fun pref(key: String) = (preferences[key] as? JsonPrimitive)?.content
        return AccountSettings(
            account.discoverable ?: false,
            account.indexable ?: false,
            account.locked,
            pref("posting:default:visibility") ?: account.source?.privacy ?: "public",
            pref("posting:default:language") ?: account.source?.language.orEmpty(),
            pref("posting:default:sensitive")?.toBooleanStrictOrNull()
                ?: account.source?.sensitive
                ?: false,
            pref("posting:default:quote_policy") ?: "public",
        )
    }

    suspend fun updateAccountSettings(settings: AccountSettings): Account =
        send(
            "/api/v1/accounts/update_credentials",
            HttpMethod.Patch,
            buildJsonObject {
                put("discoverable", settings.discoverable)
                put("indexable", settings.indexable)
                put("locked", settings.locked)
                putJsonObject("source") {
                    put("indexable", settings.indexable)
                    put("privacy", settings.privacy)
                    put("language", settings.language)
                    put("sensitive", settings.sensitive)
                    put("quote_policy", settings.quotePolicy)
                }
            },
        )

    suspend fun filters(): List<ContentFilter> = filterMutex.withLock {
        get<List<ContentFilter>>("/api/v2/filters", true).also { values ->
            knownFilters.clear()
            values.forEach { knownFilters[it.id] = it }
        }
    }

    suspend fun saveFilter(filter: ContentFilter): ContentFilter = filterMutex.withLock {
        val old =
            if (filter.id.isNotBlank())
                knownFilters[filter.id]
                    ?: get<ContentFilter>("/api/v2/filters/${segment(filter.id)}", true)
            else null
        val body = buildJsonObject {
            put("title", filter.title)
            put("context", JsonArray(filter.context.map(::JsonPrimitive)))
            put("filter_action", filter.action)
            putJsonArray("keywords_attributes") {
                filter.keywords.forEach { keyword ->
                    add(
                        buildJsonObject {
                            keyword.id?.let { put("id", it) }
                            put("keyword", keyword.keyword)
                            put("whole_word", keyword.wholeWord)
                        }
                    )
                }
                old?.keywords
                    ?.filter { previous ->
                        previous.id != null && filter.keywords.none { it.id == previous.id }
                    }
                    ?.forEach { removed ->
                        add(
                            buildJsonObject {
                                put("id", removed.id)
                                put("_destroy", true)
                            }
                        )
                    }
            }
        }
        send<ContentFilter>(
                "/api/v2/filters" + if (filter.id.isBlank()) "" else "/${segment(filter.id)}",
                if (filter.id.isBlank()) HttpMethod.Post else HttpMethod.Put,
                body,
            )
            .also { knownFilters[it.id] = it }
    }

    suspend fun deleteFilter(id: String) {
        filterMutex.withLock {
            request("/api/v2/filters/${segment(id)}", HttpMethod.Delete, true, allowEmpty = true)
            knownFilters.remove(id)
        }
    }

    suspend fun notificationPolicy(): NotificationPolicy = get("/api/v1/notifications/policy", true)

    suspend fun updateNotificationPolicy(policy: NotificationPolicy): NotificationPolicy =
        send("/api/v1/notifications/policy", HttpMethod.Put, apiJson.encodeToJsonElement(policy))

    suspend fun serverInfo(): ServerInfo {
        val info = get<JsonObject>("/api/v2/instance")
        fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.content.orEmpty()
        val config = info["configuration"] as? JsonObject
        val max =
            ((config?.get("statuses") as? JsonObject)?.get("max_characters") as? JsonPrimitive)
                ?.content
                ?.toIntOrNull()
                ?.takeIf { it > 0 } ?: 500
        val stream =
            (info["urls"] as? JsonObject)?.string("streaming_api")?.takeIf { it.isNotBlank() }
                ?: (config?.get("urls") as? JsonObject)?.string("streaming")?.takeIf {
                    it.isNotBlank()
                }
        val contact = info["contact"] as? JsonObject
        val usage = (info["usage"] as? JsonObject)?.get("users") as? JsonObject
        return ServerInfo(
                info.string("title"),
                info.string("description"),
                info.string("version"),
                info.string("domain"),
                max,
                stream,
                (info["oauth"] as? JsonObject)?.get("pkce") == JsonPrimitive(true),
                thumbnail = (info["thumbnail"] as? JsonObject)?.string("url")?.takeIf { it.isNotBlank() },
                activeMonthlyUsers = usage?.string("active_month")?.toIntOrNull()?.takeIf { it >= 0 },
                contactEmail = contact?.string("email").orEmpty(),
                contactAccount = (contact?.get("account") as? JsonObject)?.let { decodeApi<Account>(it) },
                rules = (info["rules"] as? JsonArray)?.let { decodeApi<List<ServerRule>>(it) }.orEmpty(),
            )
            .also { cachedServer = it }
    }

    suspend fun serverDescription(): String =
        (get<JsonObject>("/api/v1/instance/extended_description")["content"] as? JsonPrimitive)
            ?.content
            .orEmpty()

    fun events(source: StreamSource): Flow<StreamEvent> = channelFlow {
        val subscription = streamSubscription(source)
        if (token == null) return@channelFlow
        val collectionJob = currentCoroutineContext().job
        val onClose = streamLifetime.invokeOnCompletion { collectionJob.cancel() }
        try {
            var retry = 1000L
            var connected = false
            while (currentCoroutineContext().isActive && streamLifetime.isActive) {
                try {
                    val streamRoot = (cachedServer ?: serverInfo()).streamingUrl ?: base
                    val httpsRoot =
                        if (streamRoot.startsWith("wss://"))
                            "https://" + streamRoot.removePrefix("wss://")
                        else streamRoot
                    val authority = normalizeInstance(httpsRoot)
                    http.webSocket({
                        url("wss://$authority/api/v1/streaming")
                        url.parameters.append("stream", subscription.first)
                        subscription.second?.let { url.parameters.append("tag", it) }
                        bearerAuth(token)
                    }) {
                        if (connected) send(StreamEvent.Reconnected(source))
                        connected = true
                        retry = 1000
                        maxFrameSize = 512 * 1024L
                        var window = TimeSource.Monotonic.markNow()
                        var count = 0
                        for (frame in incoming) {
                            if (window.elapsedNow().inWholeSeconds >= 10) {
                                window = TimeSource.Monotonic.markNow()
                                count = 0
                            }
                            if (++count > 200)
                                throw ApiException(-1, "Streaming message rate exceeds the limit")
                            if (frame !is Frame.Text) continue
                            if (frame.data.size > 512 * 1024)
                                throw ApiException(-1, "Streaming frame exceeds the limit")
                            val envelope = boundedJson(frame.readText()) as? JsonObject ?: continue
                            parseStreamEvent(source, envelope)?.let { send(it) }
                            retry = 1000
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                }
                delay(retry)
                retry = (retry * 2).coerceAtMost(30_000)
            }
        } finally {
            onClose.dispose()
        }
    }

    fun close() {
        streamLifetime.cancel()
        http.close()
        sourceHttp.close()
    }
}

internal fun Timeline.streamSource(): StreamSource? = when (this) {
    Timeline.Home -> StreamSource.User
    is Timeline.Public -> if (local) StreamSource.Local else StreamSource.Federated
    is Timeline.Hashtag -> StreamSource.Hashtag(tag.lowercase())
    else -> null
}

internal fun streamSubscription(source: StreamSource): Pair<String, String?> = when (source) {
    StreamSource.User -> "user" to null
    StreamSource.Local -> "public:local" to null
    StreamSource.Federated -> "public" to null
    is StreamSource.Hashtag -> "hashtag" to source.name
    StreamSource.LocalWrite -> error("Local changes have no wire subscription")
}

internal fun parseStreamEvent(source: StreamSource, envelope: JsonObject): StreamEvent? {
    val subscription = streamSubscription(source)
    if ("stream" in envelope) {
        val stream = (envelope["stream"] as? JsonArray)
            ?.map { (it as? JsonPrimitive)?.content } ?: return null
        if (stream != listOfNotNull(subscription.first, subscription.second)) return null
    }
    val event = (envelope["event"] as? JsonPrimitive)?.content ?: return null
    if (event !in setOf("update", "status.update", "delete", "notification")) return null
    val payload = envelope["payload"] ?: return null
    val value = if (payload is JsonPrimitive && payload.isString && event != "delete")
        boundedJson(payload.content) else payload
    return when (event) {
        "update" -> StreamEvent.Created(source, decodeApi(value))
        "status.update" -> StreamEvent.Edited(source, decodeApi(value))
        "notification" -> StreamEvent.Notify(source, decodeApi(value))
        else -> (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it.length <= 200 }
            ?.let { StreamEvent.Deleted(source, it) }
    }
}
