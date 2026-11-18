package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.quanquan.quantoot.LoginPrompt
import com.quanquan.quantoot.auth.SessionState
import com.quanquan.quantoot.model.*
import kotlin.math.roundToLong
import kotlinx.coroutines.*

enum class SettingsSection(val titleKey: String, val requiresAccount: Boolean) {
    Privacy("mastodon_settings_privacy", true),
    PostingDefaults("mastodon_settings_posting_defaults", true),
    Filters("mastodon_settings_filters", true),
    Notifications("mastodon_settings_notifications", true),
    Server("mastodon_settings_about_server", false),
    PrivacyPolicy("settings_privacy_policy", false),
    Licenses("settings_open_source_licenses", false),
}

@Composable
fun SettingsScreen(ui: UiContext, state: SessionState, section: SettingsSection?) {
    if (section?.requiresAccount == true && ui.me == null) {
        LoginPrompt(ui)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (section) {
            null -> SettingsIndex(ui, state)
            SettingsSection.Privacy,
            SettingsSection.PostingDefaults -> AccountDefaults(ui, section == SettingsSection.Privacy)
            SettingsSection.Filters -> FiltersSettings(ui)
            SettingsSection.Notifications -> NotificationSettings(ui)
            SettingsSection.Server -> ServerSettings(ui)
            SettingsSection.PrivacyPolicy -> PrivacyPolicy(ui)
            SettingsSection.Licenses -> {
                listOf(
                        "Kotlin · Apache 2.0" to
                            "https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt",
                        "Compose Multiplatform · Apache 2.0" to
                            "https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt",
                        "Ktor · Apache 2.0" to "https://github.com/ktorio/ktor/blob/main/LICENSE",
                        "Coil · Apache 2.0" to
                            "https://github.com/coil-kt/coil/blob/main/LICENSE.txt",
                        "QRose · MIT" to
                            "https://github.com/alexzhirkevich/qrose/blob/master/LICENSE",
                    )
                    .forEach { (label, url) ->
                        TextButton(onClick = { ui.open(url) }) { Text(label) }
                    }
            }
        }
    }
}

@Composable
private fun SettingsIndex(ui: UiContext, state: SessionState) {
    var choosingLanguage by remember { mutableStateOf(false) }
    val loader = SingletonImageLoader.get(LocalPlatformContext.current)
    var cacheBytes by remember(loader) { mutableStateOf<Long?>(null) }
    var clearingCache by remember(loader) { mutableStateOf(false) }
    var cacheRevision by remember(loader) { mutableIntStateOf(0) }
    LaunchedEffect(loader, cacheRevision) {
        cacheBytes = withContext(Dispatchers.IO) {
            runCatching { loader.diskCache?.size ?: 0L }.getOrNull()
        }
    }
    val cacheCleared = tr("settings_media_cache_cleared")
    val languages = languageOptions()
    val locale = state.locale ?: "system"
    SettingsGroup(tr("settings_app_preferences")) {
        SettingsRow(
            label = tr("settings_language"),
            icon = PostIcons.Public,
            detail = languages.firstOrNull { it.first == locale }?.second ?: locale,
            onClick = { choosingLanguage = true },
        )
        SettingsRow(
            tr("settings_clear_media_cache"), Icons.Default.Delete,
            showChevron = false,
            trailingValue = cacheBytes?.let(::formatCacheSize),
            enabled = !clearingCache,
        ) {
            clearingCache = true
            ui.run {
                try {
                    loader.memoryCache?.clear()
                    withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                    ui.message = cacheCleared
                } finally {
                    clearingCache = false
                    cacheRevision++
                }
            }
        }
    }
    if (ui.me != null) {
        SettingsGroup(tr("mastodon_account_settings")) {
            listOf(
                SettingsSection.Privacy to Icons.Default.Lock,
                SettingsSection.PostingDefaults to Icons.Default.Edit,
                SettingsSection.Filters to Icons.AutoMirrored.Filled.List,
                SettingsSection.Notifications to Icons.Default.Notifications,
            ).forEach { (section, icon) ->
                SettingsRow(tr(section.titleKey), icon) { ui.navigate(Screen.Settings(section)) }
            }
        }
    }
    SettingsGroup(tr("settings_about")) {
        SettingsRow(tr(SettingsSection.Server.titleKey), PostIcons.Public, detail = state.instance) {
            ui.navigate(Screen.Settings(SettingsSection.Server))
        }
        SettingsRow(tr(SettingsSection.PrivacyPolicy.titleKey), Icons.Default.Lock) {
            ui.navigate(Screen.Settings(SettingsSection.PrivacyPolicy))
        }
        SettingsRow(tr(SettingsSection.Licenses.titleKey), Icons.AutoMirrored.Filled.List) {
            ui.navigate(Screen.Settings(SettingsSection.Licenses))
        }
        SettingsRow(tr("settings_developer_support"), Icons.Default.AccountCircle, detail = "BobMaster", showChevron = false) {
            ui.platform.share("QuanToot support: BobMaster <bobmaster@hibobmaster.com>")
        }
        SettingsRow(tr("settings_feedback"), Icons.Default.Email, showChevron = false) {
            ui.open("https://quanquan.space/")
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        BrandMark(Modifier.size(28.dp))
        Text(tr("settings_version_label", "version" to "0.6.5"), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (choosingLanguage) ChoiceSheet(
        tr("settings_language"), locale, languages,
        onChange = { ui.run { ui.store.setLocale(it) } },
        onDismiss = { choosingLanguage = false },
    )
}

private fun formatCacheSize(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB", "EB")
    var amount = bytes / 1_000.0
    var unit = 0
    while (amount >= 999.95 && unit < units.lastIndex) {
        amount /= 1_000
        unit++
    }
    val tenths = (amount * 10).roundToLong()
    val number = if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    return "$number ${units[unit]}"
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, Modifier.padding(top = 6.dp, bottom = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        content()
    }
}

@Composable
private fun SettingsRow(
    label: String,
    icon: ImageVector,
    detail: String? = null,
    showChevron: Boolean = true,
    trailingValue: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 3.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailingValue != null) {
                Text(trailingValue, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(Modifier.padding(start = 37.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
private fun AccountDefaults(ui: UiContext, privacy: Boolean) {
    val settingsSaved = tr("mastodon_settings_saved")
    var settings by remember { mutableStateOf<AccountSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        try {
            settings = ui.api.accountSettings()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    ErrorNotice(error) { attempt++ }
    val value = settings
    if (value == null) {
        if (error == null) Loading()
        return
    }
    if (privacy) {
        Toggle(tr("mastodon_settings_discoverable"), value.discoverable) {
            settings = value.copy(discoverable = it)
        }
        Toggle(tr("mastodon_settings_indexable"), value.indexable) {
            settings = value.copy(indexable = it)
        }
        Toggle(tr("mastodon_profile_locked"), value.locked) { settings = value.copy(locked = it) }
    } else {
        Text(tr("mastodon_settings_posting_explanation"))
        Choice(tr("mastodon_settings_visibility"), value.privacy, visibilityOptions()) {
            settings = value.copy(privacy = it)
        }
        OutlinedTextField(
            value.language,
            { settings = value.copy(language = it) },
            label = { Text(tr("mastodon_settings_language")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Toggle(tr("mastodon_sensitive_content"), value.sensitive) {
            settings = value.copy(sensitive = it)
        }
        Choice(tr("mastodon_settings_quote_policy"), value.quotePolicy, quoteOptions()) {
            settings = value.copy(quotePolicy = it)
        }
    }
    Button(
        onClick = {
            busy = true
            ui.run {
                try {
                    ui.store.updateAccount(ui.api.updateAccountSettings(value))
                    ui.message = settingsSaved
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
    ) {
        Text(tr("mastodon_save"))
    }
}

@Composable
private fun NotificationSettings(ui: UiContext) {
    val settingsSaved = tr("mastodon_settings_saved")
    var policy by remember { mutableStateOf<NotificationPolicy?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        try {
            policy = ui.api.notificationPolicy()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    ErrorNotice(error) { attempt++ }
    val value = policy
    if (value == null) {
        if (error == null) Loading()
        return
    }
    val options = listOf("accept" to "Accept", "filter" to "Filter", "drop" to "Drop")
    Choice(tr("mastodon_settings_notify_not_following"), value.forNotFollowing, options) {
        policy = value.copy(forNotFollowing = it)
    }
    Choice(tr("mastodon_settings_notify_not_followers"), value.forNotFollowers, options) {
        policy = value.copy(forNotFollowers = it)
    }
    Choice(tr("mastodon_settings_notify_new_accounts"), value.forNewAccounts, options) {
        policy = value.copy(forNewAccounts = it)
    }
    Choice(tr("mastodon_settings_notify_private_mentions"), value.forPrivateMentions, options) {
        policy = value.copy(forPrivateMentions = it)
    }
    Choice("Limited accounts", value.forLimitedAccounts, options) {
        policy = value.copy(forLimitedAccounts = it)
    }
    Button(
        onClick = {
            busy = true
            ui.run {
                try {
                    policy = ui.api.updateNotificationPolicy(value)
                    ui.message = settingsSaved
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
    ) {
        Text(tr("mastodon_save"))
    }
}

@Composable
private fun FiltersSettings(ui: UiContext) {
    var filters by remember { mutableStateOf<List<ContentFilter>?>(null) }
    var editing by remember { mutableStateOf<ContentFilter?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<ContentFilter?>(null) }
    LaunchedEffect(attempt) {
        try {
            filters = ui.api.filters()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    ErrorNotice(error) { attempt++ }
    Button(onClick = { editing = ContentFilter() }) { Text(tr("mastodon_settings_filter_add")) }
    if (filters == null && error == null) Loading()
    if (filters?.isEmpty() == true) Empty(tr("mastodon_settings_filter_empty"))
    filters.orEmpty().forEach { filter ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(filter.title, style = MaterialTheme.typography.titleMedium)
                Text(filter.keywords.joinToString(", ") { it.keyword })
                Text(filter.context.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { editing = filter }) { Text(tr("mastodon_edit")) }
                    TextButton(onClick = { pendingDelete = filter }) {
                        Text(tr("mastodon_settings_filter_delete"))
                    }
                }
            }
        }
    }
    pendingDelete?.let { filter ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("mastodon_settings_filter_delete")) },
            text = { Text(filter.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.run {
                            ui.api.deleteFilter(filter.id)
                            pendingDelete = null
                            attempt++
                        }
                    }
                ) {
                    Text(tr("mastodon_delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(tr("mastodon_cancel")) }
            },
        )
    }
    editing?.let { initial ->
        key(initial) {
            var title by remember { mutableStateOf(initial.title) }
            var keywords by remember {
                mutableStateOf(initial.keywords.joinToString(", ") { it.keyword })
            }
            var warn by remember { mutableStateOf(initial.action != "hide") }
            var contexts by remember { mutableStateOf(initial.context.toSet()) }
            var wholeWord by remember { mutableStateOf(initial.keywords.all { it.wholeWord }) }
            var expires by remember { mutableStateOf(initial.expiresAt.orEmpty()) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(tr("mastodon_settings_filters")) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            title,
                            { title = it },
                            label = { Text(tr("mastodon_settings_filter_title")) },
                        )
                        OutlinedTextField(
                            keywords,
                            { keywords = it },
                            label = { Text(tr("mastodon_settings_filter_keywords")) },
                        )
                        Toggle(tr("mastodon_settings_filter_warn"), warn) { warn = it }
                        Toggle("Whole words", wholeWord) { wholeWord = it }
                        Text(tr("mastodon_settings_filter_contexts"))
                        listOf("home", "notifications", "public", "thread", "account").forEach {
                            context ->
                            Toggle(context, context in contexts) { checked ->
                                contexts = if (checked) contexts + context else contexts - context
                            }
                        }
                        OutlinedTextField(
                            expires,
                            { expires = it },
                            label = { Text("Expires at (ISO 8601, optional)") },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            ui.run {
                                ui.api.saveFilter(
                                    initial.copy(
                                        title = title.trim(),
                                        action = if (warn) "warn" else "hide",
                                        context = contexts.toList(),
                                        keywords =
                                            keywords
                                                .split(',')
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                                .map { word ->
                                                    FilterKeyword(
                                                        initial.keywords
                                                            .firstOrNull { it.keyword == word }
                                                            ?.id,
                                                        word,
                                                        wholeWord,
                                                    )
                                                },
                                        expiresAt = expires.ifBlank { null },
                                    )
                                )
                                editing = null
                                attempt++
                            }
                        },
                        enabled =
                            title.isNotBlank() &&
                                keywords.split(',').any { it.isNotBlank() } &&
                                contexts.isNotEmpty(),
                    ) {
                        Text(tr("mastodon_save"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editing = null }) { Text(tr("mastodon_cancel")) }
                },
            )
        }
    }
}

@Composable
private fun ServerSettings(ui: UiContext) {
    var info by remember(ui.api) { mutableStateOf<ServerInfo?>(null) }
    var description by remember(ui.api) { mutableStateOf("") }
    var error by remember(ui.api) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.api, attempt) {
        try {
            info = ui.api.serverInfo()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(ui.api) {
        try {
            description = ui.api.serverDescription()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            description = ""
        }
    }
    ErrorNotice(error) { attempt++ }
    if (info == null && error == null) Loading()
    info?.let { server ->
        ServerInformation(ui, server, description)
    }
}

@Composable
private fun PrivacyPolicy(ui: UiContext) {
    Text("Last updated: July 14, 2026", style = MaterialTheme.typography.bodySmall)
    val sections =
        listOf(
            "Data collected by QuanToot" to
                "QuanToot is a client for Mastodon servers and does not operate a social network or backend service. BobMaster does not collect, receive, sell, or share personal information through the app. QuanToot contains no advertising, analytics, or tracking SDKs.\n\nThe app stores the server you select, preferences, and cached timeline or media data locally. Mastodon account sessions and access tokens are kept in the operating system's secure storage. Android backup is disabled for the app's data. This information is not sent to BobMaster.",
            "Independent Mastodon servers" to
                "QuanToot communicates directly with the independently operated Mastodon server you choose. Its operator may collect or process your IP address, account and profile information, posts, media, interactions, and usage records. Remote media or linked-content hosts may also receive ordinary connection information when content is loaded.\n\nThese parties are not controlled by QuanToot or BobMaster. Their own privacy policies and terms apply. Review your server's policies before signing in or posting.",
            "Photos and media" to
                "QuanToot uses the system picker when you choose media to upload, so the app receives only the items you select. When you explicitly save media, the app may write that item to your device's gallery.",
            "Your choices and data deletion" to
                "Remove an account, clear QuanToot's data in system settings, or uninstall the app to delete locally stored data. For data held by a Mastodon server, use that server's controls or contact its operator.",
            "Changes to this policy" to
                "This policy may be updated when QuanToot's features or legal requirements change. The date above identifies the latest version.",
        )
    sections.forEach { (title, body) ->
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body)
    }
    Text("Contact", style = MaterialTheme.typography.titleMedium)
    Text("BobMaster · bobmaster@hibobmaster.com")
    TextButton(onClick = { ui.open("https://quanquan.space/") }) { Text(tr("settings_feedback")) }
}
