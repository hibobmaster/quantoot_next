package com.quanquan.quantoot.auth

import com.quanquan.quantoot.data.FeedCacheStorage
import com.quanquan.quantoot.data.MastodonApi
import com.quanquan.quantoot.data.normalizeInstance
import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.platform.PlatformServices
import com.quanquan.quantoot.platform.createHttpClient
import com.quanquan.quantoot.platform.secureRandomBytes
import com.quanquan.quantoot.platform.sha256
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class Session(
    @SerialName("instance_host") val instance: String,
    @SerialName("access_token") val accessToken: String,
    val account: Account,
) {
    val key: String
        get() = "${instance.lowercase()}::${account.id}"

    override fun toString(): String = "Session(instance=$instance, accountId=${account.id})"
}

data class SessionState(
    val restored: Boolean = false,
    val instance: String? = null,
    val sessions: List<Session> = emptyList(),
    val activeKey: String? = null,
    val authorizationRevision: Long = 0,
    val locale: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val activeSession: Session?
        get() = sessions.firstOrNull { it.key == activeKey }
}

@Serializable
private data class PendingAuthorization(
    val instance: String,
    val clientId: String,
    val clientSecret: String,
    val state: String,
    val verifier: String? = null,
    val createdAt: Long,
) {
    override fun toString(): String = "PendingAuthorization(instance=$instance)"
}

@Serializable
private data class AuthSnapshot(
    val sessions: List<Session> = emptyList(),
    @SerialName("active_session_key") val activeKey: String? = null,
    val pending: PendingAuthorization? = null,
) {
    override fun toString(): String = "AuthSnapshot(accounts=${sessions.size})"
}

@OptIn(ExperimentalTime::class)
class AccountStore(
    val platform: PlatformServices,
    private val httpClientFactory: () -> HttpClient = ::createHttpClient,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()
    internal val feedCache = FeedCacheStorage(platform.preferences) { key ->
        state.value.sessions.any { it.key == key }
    }
    private val mutex = Mutex()
    private var snapshot = AuthSnapshot()
    private var guestInstance: String? = null
    private var storageBlocked = false

    suspend fun restore() = mutex.withLock { restoreLocked() }

    private suspend fun restoreLocked() {
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            val savedGuest = platform.preferences.read(INSTANCE_KEY)
            val locale = platform.preferences.read(LOCALE_KEY)?.let(::validateLocale)
            val guest = savedGuest?.let(::normalizeInstance)
            val raw = platform.secureStorage.read(SECURE_KEY)
            val restored = if (raw != null) decodeSnapshot(raw) else migrateLegacy()
            snapshot = restored
            guestInstance = guest
            storageBlocked = false
            mutableState.value =
                SessionState(
                    restored = true,
                    instance = activeInstance(restored),
                    sessions = restored.sessions,
                    activeKey = restored.activeKey,
                    authorizationRevision = authorizationRevision(restored),
                    locale = locale,
                )
        } catch (cancelled: CancellationException) {
            mutableState.value = state.value.copy(busy = false)
            throw cancelled
        } catch (_: Exception) {
            storageBlocked = true
            mutableState.value =
                state.value.copy(
                    restored = true,
                    busy = false,
                    error =
                        "Unable to restore saved accounts. Retry after checking secure storage.",
                )
        }
    }

    suspend fun browse(input: String) = operation {
        val host = normalizeInstance(input)
        val next = snapshot.copy(activeKey = null)
        platform.preferences.write(INSTANCE_KEY, host)
        persist(next)
        guestInstance = host
        publish(next)
    }

    suspend fun login(input: String) = operation {
        val host = normalizeInstance(input)
        val client = httpClientFactory()
        try {
            val supportsPkce =
                try {
                    val info = requestJson(client, host, "/api/v2/instance").jsonObject
                    (info["api_versions"] as? JsonObject)
                        ?.get("mastodon")
                        ?.jsonPrimitive
                        ?.content
                        ?.toIntOrNull()
                        ?.let { it >= 3 } == true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            val app =
                requestJson(
                        client,
                        host,
                        "/api/v1/apps",
                        parametersOf(
                            "client_name" to listOf("QuanToot"),
                            "redirect_uris" to listOf(REDIRECT),
                            "scopes" to listOf(SCOPES),
                        ),
                    )
                    .jsonObject
            val clientId = app.requiredString("client_id")
            val clientSecret = app.requiredString("client_secret")
            val verifier = if (supportsPkce) randomSecret() else null
            val pending =
                PendingAuthorization(
                    host,
                    clientId,
                    clientSecret,
                    randomSecret(),
                    verifier,
                    nowMillis(),
                )
            val next = snapshot.copy(pending = pending)
            persist(next)
            snapshot = next
            val url =
                URLBuilder("https://$host/oauth/authorize")
                    .apply {
                        parameters.append("response_type", "code")
                        parameters.append("client_id", clientId)
                        parameters.append("redirect_uri", REDIRECT)
                        parameters.append("scope", SCOPES)
                        parameters.append("state", pending.state)
                        if (verifier != null) {
                            parameters.append(
                                "code_challenge",
                                Base64.UrlSafe.encode(sha256(verifier.encodeToByteArray()))
                                    .trimEnd('='),
                            )
                            parameters.append("code_challenge_method", "S256")
                        }
                    }
                    .buildString()
            platform.openAuthorization(url)
        } finally {
            client.close()
        }
    }

    suspend fun acceptCallback(raw: String) = operation {
        val parameters = callbackParameters(raw)
        val pending = snapshot.pending ?: fail("There is no pending sign-in. Start sign-in again.")
        val returnedState = parameters["state"] ?: fail("Sign-in callback is missing its state.")
        if (!constantTimeEqual(pending.state, returnedState))
            fail("Sign-in callback did not match this sign-in attempt.")
        val age = nowMillis() - pending.createdAt
        if (age < 0 || age >= AUTH_LIFETIME_MS) fail("Sign-in expired. Start sign-in again.")
        if (parameters["error"] != null)
            fail("Authorization was declined. You can try sign-in again.")
        val code =
            parameters["code"]?.takeIf { it.isNotBlank() }
                ?: fail("Sign-in callback is missing its code.")
        val consumed = snapshot.copy(pending = null)
        persist(consumed)
        snapshot = consumed
        val client = httpClientFactory()
        try {
            val form = Parameters.build {
                append("grant_type", "authorization_code")
                append("client_id", pending.clientId)
                append("client_secret", pending.clientSecret)
                append("redirect_uri", REDIRECT)
                append("scope", SCOPES)
                append("code", code)
                pending.verifier?.let { append("code_verifier", it) }
            }
            val token =
                requestJson(client, pending.instance, "/oauth/token", form)
                    .jsonObject
                    .requiredString("access_token")
            validateToken(token)
            val account =
                AUTH_JSON.decodeFromJsonElement<Account>(
                    requestJson(
                        client,
                        pending.instance,
                        "/api/v1/accounts/verify_credentials",
                        token = token,
                    )
                )
            validateAccount(account)
            val session = Session(pending.instance, token, account)
            val sessions = snapshot.sessions.toMutableList()
            val index = sessions.indexOfFirst { it.key == session.key }
            if (index < 0) sessions.add(session) else sessions[index] = session
            val next = snapshot.copy(sessions = sessions, activeKey = session.key)
            persist(next)
            publish(next)
        } finally {
            client.close()
        }
    }

    suspend fun switchAccount(key: String) = operation {
        if (snapshot.sessions.none { it.key == key }) fail("The saved account could not be found.")
        val next = snapshot.copy(activeKey = key)
        persist(next)
        publish(next)
    }

    suspend fun removeAccount(key: String) = operation {
        val session = snapshot.sessions.firstOrNull { it.key == key } ?: return@operation
        val remaining = snapshot.sessions.filterNot { it.key == key }
        val nextActive =
            if (snapshot.activeKey == key) remaining.firstOrNull()?.key else snapshot.activeKey
        val next = snapshot.copy(sessions = remaining, activeKey = nextActive)
        feedCache.removeAccount(session.instance, session.key) {
            withContext(NonCancellable) {
                persist(next)
                publish(next)
            }
        }
    }

    suspend fun setLocale(code: String) = operation {
        val locale = validateLocale(code)
        platform.preferences.write(LOCALE_KEY, locale)
        mutableState.value = state.value.copy(locale = locale)
    }

    suspend fun updateAccount(account: Account) = operation {
        validateAccount(account)
        val current = state.value.activeSession ?: fail("Sign in before updating an account.")
        if (current.account.id != account.id)
            fail("The account does not match the current sign-in.")
        val sessions =
            snapshot.sessions.map { if (it.key == current.key) it.copy(account = account) else it }
        val next = snapshot.copy(sessions = sessions)
        persist(next)
        publish(next)
    }

    fun client(): MastodonApi? {
        val current = state.value
        val host = current.instance ?: return null
        return MastodonApi(host, current.activeSession?.accessToken, httpClientFactory())
    }

    fun clearError() {
        mutableState.value = state.value.copy(error = null)
    }

    private suspend fun operation(action: suspend () -> Unit) = mutex.withLock {
        if (!state.value.restored) restoreLocked()
        if (storageBlocked) {
            mutableState.value =
                state.value.copy(
                    error = "Saved accounts are unavailable. Retry restoring secure storage first."
                )
            return@withLock
        }
        mutableState.value = state.value.copy(busy = true, error = null)
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AuthFailure) {
            mutableState.value = state.value.copy(error = failure.message)
        } catch (_: Exception) {
            mutableState.value =
                state.value.copy(
                    error =
                        "The operation failed. Check your connection or secure storage and retry."
                )
        } finally {
            mutableState.value = state.value.copy(busy = false)
        }
    }

    private suspend fun persist(next: AuthSnapshot) {
        val encoded = AUTH_JSON.encodeToString(next)
        boundedAuthJson(encoded)
        platform.secureStorage.write(SECURE_KEY, encoded)
    }

    private fun publish(next: AuthSnapshot) {
        snapshot = next
        mutableState.value =
            state.value.copy(
                instance = activeInstance(next),
                sessions = next.sessions,
                activeKey = next.activeKey,
                authorizationRevision = authorizationRevision(next),
            )
    }

    private fun authorizationRevision(next: AuthSnapshot): Long {
        val current = state.value
        val nextSession = next.sessions.firstOrNull { it.key == next.activeKey }
        val changed =
            current.activeKey != next.activeKey ||
                current.activeSession?.accessToken != nextSession?.accessToken
        return current.authorizationRevision + if (changed) 1 else 0
    }

    private fun activeInstance(value: AuthSnapshot): String? =
        value.sessions.firstOrNull { it.key == value.activeKey }?.instance ?: guestInstance

    private fun decodeSnapshot(raw: String): AuthSnapshot {
        val element = boundedAuthJson(raw)
        if (element !is JsonObject || element["sessions"] !is JsonArray)
            fail("Invalid saved account data.")
        val decoded = AUTH_JSON.decodeFromJsonElement<AuthSnapshot>(element)
        val sessions = validateSessions(decoded.sessions)
        val active =
            decoded.activeKey?.let { key ->
                sessions.firstOrNull { it.key == key }?.key
                    ?: sessions.firstOrNull()?.key
                    ?: fail("Invalid saved account selection.")
            }
        decoded.pending?.let {
            if (
                normalizeInstance(it.instance) != it.instance ||
                    it.state.isBlank() ||
                    it.clientId.isBlank() ||
                    it.clientSecret.isBlank() ||
                    it.createdAt < 0
            )
                fail("Invalid saved sign-in.")
            if (
                it.verifier != null &&
                    (it.verifier.length !in 43..128 ||
                        !it.verifier.all { char -> char.isLetterOrDigit() || char in "-._~" })
            )
                fail("Invalid saved sign-in.")
        }
        return decoded.copy(sessions = sessions, activeKey = active)
    }

    private fun validateSessions(values: List<Session>): List<Session> {
        val keys = mutableSetOf<String>()
        return values.map {
            validateToken(it.accessToken)
            validateAccount(it.account)
            val validated = it.copy(instance = normalizeInstance(it.instance))
            if (!keys.add(validated.key)) fail("Duplicate saved account.")
            validated
        }
    }

    private suspend fun migrateLegacy(): AuthSnapshot {
        val list = platform.preferences.read("mastodon_sessions_v2")
        val single = platform.preferences.read("mastodon_session_v1")
        if (list == null && single == null) return AuthSnapshot()
        val sessions =
            validateSessions(
                if (list != null)
                    AUTH_JSON.decodeFromJsonElement<List<Session>>(boundedAuthJson(list))
                else listOf(AUTH_JSON.decodeFromJsonElement<Session>(boundedAuthJson(single!!)))
            )
        val activeKey = platform.preferences.read("mastodon_active_session_v2")
        val next =
            AuthSnapshot(
                sessions,
                sessions.firstOrNull { it.key == activeKey }?.key ?: sessions.firstOrNull()?.key,
            )
        persist(next)
        platform.preferences.write("mastodon_sessions_v2", null)
        platform.preferences.write("mastodon_session_v1", null)
        platform.preferences.write("mastodon_active_session_v2", null)
        return next
    }
}

private const val SECURE_KEY = "mastodon_auth_state_v3"
private const val INSTANCE_KEY = "mastodon_instance_host_v1"
private const val LOCALE_KEY = "app_locale"
private const val REDIRECT = "quantoot://mastodon-auth"
private const val SCOPES = "read write follow"
private const val AUTH_LIFETIME_MS = 10 * 60 * 1000L
private const val MAX_AUTH_BYTES = 2 * 1024 * 1024
private val AUTH_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

private class AuthFailure(override val message: String) : Exception(message)

private fun fail(message: String): Nothing = throw AuthFailure(message)

private fun randomSecret(): String = Base64.UrlSafe.encode(secureRandomBytes(32)).trimEnd('=')

private fun validateLocale(value: String): String =
    when (value) {
        "system",
        "en",
        "zh",
        "zh-TW" -> value
        "zh_TW" -> "zh-TW"
        else -> fail("Unsupported language selection.")
    }

private fun validateToken(value: String) {
    if (
        value.isBlank() ||
            value.length > 16384 ||
            value.any { it.isWhitespace() || it.code < 32 || it.code == 127 }
    )
        fail("Invalid access token.")
}

private fun validateAccount(value: Account) {
    if (
        value.id.isBlank() ||
            value.id.length > 512 ||
            value.id.any { it.isWhitespace() || it.code < 32 }
    )
        fail("Invalid account response.")
}

private fun JsonObject.requiredString(key: String): String =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        ?: fail("Invalid authorization response.")

private fun callbackParameters(raw: String): Map<String, String> {
    if (raw.length > 16384 || raw.any { it.code < 32 }) fail("Invalid sign-in callback.")
    val prefix = raw.substringBefore('?')
    if (prefix != REDIRECT && prefix != "$REDIRECT/") fail("Invalid sign-in callback address.")
    if ('#' in raw || '?' !in raw) fail("Invalid sign-in callback.")
    val parameters =
        try {
            parseQueryString(raw.substringAfter('?'))
        } catch (_: Exception) {
            fail("Invalid sign-in callback.")
        }
    if (parameters.entries().any { it.value.size != 1 })
        fail("Duplicate sign-in callback parameters.")
    return parameters.entries().associate { it.key to it.value.single() }
}

private fun constantTimeEqual(expected: String, actual: String): Boolean {
    val a = expected.encodeToByteArray()
    val b = actual.encodeToByteArray()
    var difference = a.size xor b.size
    for (index in 0 until maxOf(a.size, b.size)) difference =
        difference or ((a.getOrNull(index)?.toInt() ?: 0) xor (b.getOrNull(index)?.toInt() ?: 0))
    return difference == 0
}

private suspend fun requestJson(
    client: HttpClient,
    instance: String,
    path: String,
    form: Parameters? = null,
    token: String? = null,
): JsonElement =
    withTimeout(20_000) {
        client
            .prepareRequest("https://$instance$path") {
                method = if (form == null) HttpMethod.Get else HttpMethod.Post
                header(HttpHeaders.Accept, ContentType.Application.Json)
                header(HttpHeaders.UserAgent, "QuanToot/1.0")
                if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                if (form != null) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(form.formUrlEncode())
                }
            }
            .execute { response ->
                if (response.status.value !in 200..299)
                    fail("Authorization request failed (${response.status.value}).")
                if (
                    response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let {
                        it > MAX_AUTH_BYTES
                    } == true
                )
                    fail("Authorization response is too large.")
                val channel = response.bodyAsChannel()
                val chunks = mutableListOf<ByteArray>()
                val buffer = ByteArray(8192)
                var size = 0
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read == 0) continue
                    size += read
                    if (size > MAX_AUTH_BYTES) fail("Authorization response is too large.")
                    chunks.add(buffer.copyOf(read))
                }
                val bytes = ByteArray(size)
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(bytes, offset)
                    offset += chunk.size
                }
                boundedAuthJson(bytes.decodeToString(throwOnInvalidSequence = true))
            }
    }

private fun boundedAuthJson(raw: String): JsonElement {
    if (raw.length > MAX_AUTH_BYTES || raw.encodeToByteArray().size > MAX_AUTH_BYTES)
        fail("Saved data or response is too large.")
    var depth = 0
    var nodes = 0
    var quoted = false
    var escaped = false
    for (char in raw) {
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true else if (char == '"') quoted = false
        } else
            when (char) {
                '"' -> {
                    quoted = true
                    nodes++
                }
                '{',
                '[' -> {
                    depth++
                    nodes++
                    if (depth > 64) fail("Saved data or response is too deeply nested.")
                }
                '}',
                ']' -> depth--
                ',' -> nodes++
            }
        if (nodes > 100_000) fail("Saved data or response contains too many values.")
    }
    return AUTH_JSON.parseToJsonElement(raw)
}
