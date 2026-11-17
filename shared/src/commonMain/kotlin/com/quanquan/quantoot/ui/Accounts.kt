package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.auth.*
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.*
import kotlinx.coroutines.*

private const val DEFAULT_INSTANCE = "m.qqs.tw"

@Composable
fun InstanceScreen(store: AccountStore, state: SessionState, platform: PlatformServices) {
    var instance by remember { mutableStateOf(state.instance.orEmpty()) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "QuanToot",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(tr("mastodon_instance_title"), style = MaterialTheme.typography.headlineMedium)
            Text(
                tr("mastodon_instance_description"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            instance,
            { instance = it },
            enabled = !state.busy,
            label = { Text(tr("mastodon_instance_label")) },
            placeholder = { Text(tr("mastodon_instance_hint")) },
            supportingText = {
                Text(tr("mastodon_instance_default_hint", "instance" to DEFAULT_INSTANCE))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorNotice(state.error) { store.clearError() }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { scope.launch { store.login(instance.ifBlank { DEFAULT_INSTANCE }) } },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(tr("mastodon_continue_login"))
            }
            OutlinedButton(
                onClick = { scope.launch { store.browse(instance.ifBlank { DEFAULT_INSTANCE }) } },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(tr("mastodon_browse_without_login"))
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun AccountsScreen(ui: UiContext, state: SessionState) {
    var removal by remember { mutableStateOf<Session?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(tr("mastodon_current_server", "instance" to state.instance.orEmpty())) }
        if (state.sessions.isEmpty()) item { Empty(tr("mastodon_no_accounts")) }
        items(state.sessions, key = { it.key }) { session ->
            Card {
                Column {
                    AccountRow(session.account) { ui.run { ui.store.switchAccount(session.key) } }
                    Text(
                        session.instance + if (session.key == state.activeKey) " ✓" else "",
                        Modifier.padding(horizontal = 16.dp),
                    )
                    TextButton(onClick = { removal = session }) {
                        Text(tr("mastodon_remove_account"))
                    }
                }
            }
        }
        item {
            Button(onClick = { ui.navigate(Screen.Instance) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("mastodon_add_account"))
            }
        }
        item {
            OutlinedButton(
                onClick = { ui.navigate(Screen.Instance) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tr("mastodon_change_server"))
            }
        }
    }
    removal?.let { session ->
        AlertDialog(
            onDismissRequest = { removal = null },
            text = {
                Text(tr("mastodon_remove_account_confirm", "account" to session.account.acct))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removal = null
                        ui.run { ui.store.removeAccount(session.key) }
                    }
                ) {
                    Text(tr("mastodon_remove_account"))
                }
            },
            dismissButton = {
                TextButton(onClick = { removal = null }) { Text(tr("mastodon_cancel")) }
            },
        )
    }
}

@Composable
fun ProfileScreen(ui: UiContext, id: String) {
    var account by remember(id) { mutableStateOf<Account?>(null) }
    var relationship by remember(id) { mutableStateOf<Relationship?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    var selected by remember(id) { mutableIntStateOf(0) }
    var showReplies by remember(id) { mutableStateOf(false) }
    var showBoosts by remember(id) { mutableStateOf(true) }
    var qr by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, attempt, ui.revision) {
        try {
            account = ui.api.account(id)
            if (ui.me?.id?.let { it != id } == true) relationship = ui.api.relationship(id)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    Column {
        ErrorNotice(error) { attempt++ }
        val person = account
        if (person == null) {
            if (error == null) Loading()
            return@Column
        }
        Column(
            Modifier.heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (person.header.isNotBlank())
                AsyncImage(
                    safeWebUrl(person.header),
                    tr("mastodon_profile_header"),
                    Modifier.fillMaxWidth().height(100.dp),
                )
            AccountRow(person) { ui.open(person.url) }
            HtmlContent(ui, person.note, emojis = person.emojis)
            Row {
                TextButton(onClick = { qr = true }, enabled = safeWebUrl(person.url) != null) {
                    Text("QR")
                }
                TextButton(
                    onClick = { safeWebUrl(person.url)?.let(ui.platform::share) },
                    enabled = safeWebUrl(person.url) != null,
                ) {
                    Text(tr("mastodon_button_share"))
                }
            }
            person.fields.forEach { field ->
                Row {
                    Text(
                        field.name + if (field.verifiedAt != null) " ✓" else "",
                        Modifier.widthIn(max = 140.dp).padding(end = 12.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        HtmlContent(ui, field.value, emojis = person.emojis)
                    }
                }
            }
            Text(
                "${tr("mastodon_joined")}: ${person.createdAt.take(10)} · ${person.statusesCount} ${tr("mastodon_posts")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                TextButton(onClick = { ui.navigate(Screen.People(id, false)) }) {
                    Text("${person.followersCount} ${tr("mastodon_followers_count_label")}")
                }
                TextButton(onClick = { ui.navigate(Screen.People(id, true)) }) {
                    Text("${person.followingCount} ${tr("mastodon_following_count")}")
                }
            }
            if (ui.me?.id == id) {
                Row {
                    TextButton(onClick = { ui.navigate(Screen.EditProfile) }) {
                        Text(tr("mastodon_edit_profile"))
                    }
                    TextButton(
                        onClick = {
                            ui.navigate(
                                Screen.Feed(Timeline.Favourites, trKey("mastodon_button_favorite"))
                            )
                        }
                    ) {
                        Text(tr("mastodon_button_favorite"))
                    }
                    TextButton(
                        onClick = { ui.navigate(Screen.Feed(Timeline.Bookmarks, "Bookmarks")) }
                    ) {
                        Text("Bookmarks")
                    }
                }
            } else {
                val rel = relationship
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Button(
                        onClick = {
                            ui.requireLogin {
                                ui.run {
                                    relationship =
                                        ui.api.relationshipAction(
                                            id,
                                            if (rel?.following == true || rel?.requested == true)
                                                "unfollow"
                                            else "follow",
                                        )
                                }
                            }
                        }
                    ) {
                        Text(
                            tr(
                                if (rel?.requested == true) "mastodon_follow_requested"
                                else if (rel?.following == true) "mastodon_following"
                                else "mastodon_follow"
                            )
                        )
                    }
                    if (ui.me != null) {
                        TextButton(
                            onClick = {
                                ui.run {
                                    relationship =
                                        ui.api.relationshipAction(
                                            id,
                                            if (rel?.muting == true) "unmute" else "mute",
                                        )
                                }
                            }
                        ) {
                            Text(
                                tr(if (rel?.muting == true) "mastodon_unmute" else "mastodon_mute")
                            )
                        }
                        TextButton(
                            onClick = {
                                ui.run {
                                    relationship =
                                        ui.api.relationshipAction(
                                            id,
                                            if (rel?.blocking == true) "unblock" else "block",
                                        )
                                }
                            }
                        ) {
                            Text(
                                tr(
                                    if (rel?.blocking == true) "mastodon_unblock"
                                    else "mastodon_block"
                                )
                            )
                        }
                        TextButton(onClick = { report = true }) { Text(tr("mastodon_report")) }
                    }
                }
                if (rel?.followedBy == true) Text(tr("mastodon_follows_you"))
            }
        }
        val labels =
            listOf(
                tr("mastodon_posts"),
                tr("mastodon_media"),
                tr("mastodon_featured"),
                tr("mastodon_activity"),
                tr("settings_about"),
            )
        ScrollableTabRow(selected) {
            labels.forEachIndexed { i, label ->
                Tab(selected == i, { selected = i }, text = { Text(label) })
            }
        }
        if (selected == 3)
            Row(
                Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    showReplies,
                    { showReplies = !showReplies },
                    label = { Text(tr("mastodon_filter_show_replies")) },
                )
                FilterChip(
                    showBoosts,
                    { showBoosts = !showBoosts },
                    label = { Text(tr("mastodon_filter_show_boosts")) },
                )
            }
        if (selected == 4)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${tr("mastodon_joined")}: ${person.createdAt.take(10)}")
                person.fields.forEach { field ->
                    Column {
                        EmojiText(
                            field.name + if (field.verifiedAt != null) " ✓" else "",
                            person.emojis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        HtmlContent(ui, field.value, emojis = person.emojis)
                    }
                }
                if (person.bot) Text("Bot")
                if (person.locked) Text(tr("mastodon_profile_locked"))
            }
        else
            key(id, selected, showReplies, showBoosts) {
                FeedScreen(
                    ui,
                    Timeline.AccountPosts(
                        id,
                        onlyMedia = selected == 1,
                        pinned = selected == 2,
                        excludeReplies = selected == 0 || (selected == 3 && !showReplies),
                        excludeReblogs = selected == 0 || (selected == 3 && !showBoosts),
                    ),
                )
            }
        if (qr) ProfileQr(ui, person) { qr = false }
    }
    if (report)
        AlertDialog(
            onDismissRequest = { report = false },
            title = { Text(tr("mastodon_report")) },
            text = {
                OutlinedTextField(
                    comment,
                    { comment = it },
                    label = { Text(tr("mastodon_report_comment")) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.run {
                            ui.api.report(id, comment)
                            report = false
                            ui.message = "Report sent"
                        }
                    }
                ) {
                    Text(tr("mastodon_report"))
                }
            },
            dismissButton = {
                TextButton(onClick = { report = false }) { Text(tr("mastodon_cancel")) }
            },
        )
}

private fun trKey(key: String) = key.removePrefix("mastodon_").replace('_', ' ')

@Composable
fun PeopleScreen(ui: UiContext, id: String, following: Boolean) {
    var people by remember { mutableStateOf<List<Account>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nextCursor by remember(id, following) { mutableStateOf<String?>(null) }
    var exhausted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load(more: Boolean = false) {
        scope.launch {
            if (busy || (more && exhausted)) return@launch
            busy = true
            error = null
            try {
                val page = ui.api.people(id, following, if (more) nextCursor else null)
                people = (if (more) people + page.items else page.items).distinctBy { it.id }
                exhausted = page.nextCursor == null || (more && page.nextCursor == nextCursor)
                nextCursor = page.nextCursor
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(id, following) { load() }
    Column {
        ErrorNotice(error) { load() }
        LazyColumn {
            items(people, key = { it.id }) { AccountRow(it) { ui.navigate(Screen.Profile(it.id)) } }
            item {
                if (busy) Loading()
                else if (people.isEmpty() && error == null) Empty()
                else if (!exhausted) TextButton(onClick = { load(true) }) { Text("+20") }
            }
        }
    }
}

@Composable
fun EditProfileScreen(ui: UiContext) {
    val initial = ui.me ?: return
    var name by remember { mutableStateOf(initial.displayName) }
    var note by remember { mutableStateOf(initial.source?.note ?: plainText(initial.note)) }
    var locked by remember { mutableStateOf(initial.locked) }
    var fields by remember { mutableStateOf(initial.source?.fields ?: initial.fields) }
    var avatar by remember { mutableStateOf<PickedMedia?>(null) }
    var header by remember { mutableStateOf<PickedMedia?>(null) }
    var busy by remember { mutableStateOf(false) }
    var avatarRemoved by remember { mutableStateOf(false) }
    var headerRemoved by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            name,
            { name = it },
            label = { Text(tr("mastodon_profile_display_name")) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            note,
            { note = it },
            label = { Text(tr("mastodon_profile_bio")) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        Toggle(tr("mastodon_profile_locked"), locked) { locked = it }
        listOf(true, false).forEach { isAvatar ->
            Text(tr(if (isAvatar) "mastodon_profile_avatar" else "mastodon_profile_header"))
            val image = if (isAvatar) avatar else header
            val removed = if (isAvatar) avatarRemoved else headerRemoved
            if (!removed)
                AsyncImage(
                    image?.bytes ?: safeWebUrl(if (isAvatar) initial.avatar else initial.header),
                    null,
                    Modifier.fillMaxWidth().height(100.dp),
                )
            Row {
                TextButton(
                    onClick = {
                        ui.run {
                            val picked = ui.platform.pickMedia(1).firstOrNull() ?: return@run
                            if (isAvatar) {
                                avatar = picked
                                avatarRemoved = false
                            } else {
                                header = picked
                                headerRemoved = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(tr("mastodon_profile_replace_image"))
                }
                TextButton(
                    onClick = {
                        ui.run {
                            ui.store.updateAccount(ui.api.deleteProfileImage(isAvatar))
                            if (isAvatar) {
                                avatar = null
                                avatarRemoved = true
                            } else {
                                header = null
                                headerRemoved = true
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text(tr("mastodon_profile_remove_image"))
                }
            }
        }
        Text(tr("mastodon_profile_custom_fields"), style = MaterialTheme.typography.titleMedium)
        fields.forEachIndexed { index, field ->
            Column {
                OutlinedTextField(
                    field.name,
                    { text ->
                        fields = fields.mapIndexed { i, value ->
                            if (i == index) value.copy(name = text) else value
                        }
                    },
                    label = { Text(tr("mastodon_profile_field_name")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    field.value,
                    { text ->
                        fields = fields.mapIndexed { i, value ->
                            if (i == index) value.copy(value = text) else value
                        }
                    },
                    label = { Text(tr("mastodon_profile_field_value")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { fields = fields.filterIndexed { i, _ -> i != index } }) {
                    Text(tr("mastodon_delete"))
                }
            }
        }
        if (fields.size < 4)
            TextButton(onClick = { fields = fields + ProfileField() }) {
                Text(tr("mastodon_profile_add_field"))
            }
        Button(
            onClick = {
                busy = true
                ui.run {
                    try {
                        ui.store.updateAccount(
                            ui.api.updateProfile(
                                ProfileUpdate(name, note, locked, fields, avatar, header)
                            )
                        )
                        ui.revision++
                        ui.back()
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
        ) {
            Text(tr("mastodon_save_changes"))
        }
        if (busy) Loading()
    }
}
