package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.auth.*
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.*
import kotlinx.coroutines.*

private const val DEFAULT_INSTANCE = "m.qqs.tw"

@Composable
fun InstanceScreen(
    store: AccountStore,
    state: SessionState,
    showHeading: Boolean = true,
    onBrowse: () -> Unit = {},
) {
    var instance by remember { mutableStateOf(state.instance.orEmpty()) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        Modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compact = maxHeight < 510.dp
        val singleScroll = maxHeight < 250.dp
        val scroll = rememberScrollState()
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize()
                .then(if (singleScroll) Modifier.verticalScroll(scroll) else Modifier)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                (if (singleScroll) Modifier else Modifier.weight(1f).verticalScroll(scroll))
                    .fillMaxWidth().padding(top = if (compact) 12.dp else 32.dp),
            ) {
                if (showHeading) {
                    if (!compact) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BrandMark(Modifier.size(48.dp))
                            Wordmark()
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                    Text(
                        tr("mastodon_instance_title"),
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    tr("mastodon_instance_description"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(26.dp))
                OutlinedTextField(
                    instance,
                    { instance = it },
                    enabled = !state.busy,
                    label = { Text(tr("mastodon_instance_label")) },
                    leadingIcon = { Icon(PostIcons.Public, null, Modifier.size(20.dp)) },
                    placeholder = { Text(tr("mastodon_instance_hint")) },
                    supportingText = { Text(tr("mastodon_instance_default_hint", "instance" to DEFAULT_INSTANCE)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorNotice(state.error) { store.clearError() }
            }
            Spacer(Modifier.height(if (compact) 4.dp else 16.dp))
            Button(
                onClick = { scope.launch { store.login(instance.ifBlank { DEFAULT_INSTANCE }) } },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) { Text(tr("mastodon_continue_login")) }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = {
                    scope.launch {
                        store.browse(instance.ifBlank { DEFAULT_INSTANCE })
                        if (store.state.value.error == null) onBrowse()
                    }
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text(tr("mastodon_browse_without_login")) }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
        }
    }
}

@Composable
fun AccountsScreen(ui: UiContext, state: SessionState) {
    var removal by remember { mutableStateOf<Session?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp)) {
                Icon(PostIcons.Public, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(tr("settings_current_server"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.instance.orEmpty(), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                TextButton(onClick = { ui.navigate(Screen.Instance) }, contentPadding = PaddingValues(0.dp)) {
                    Text(tr("mastodon_change_server"))
                }
            }
        }
        if (state.sessions.isEmpty()) item { Empty(tr("mastodon_no_accounts")) }
        items(state.sessions, key = { it.key }) { session ->
            val active = session.key == state.activeKey
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f)
                        .selectable(active, role = Role.RadioButton, enabled = !state.busy,
                            onClick = { if (!active) ui.run { ui.store.switchAccount(session.key) } })
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountAvatar(session.account, 44.dp)
                    Column(Modifier.weight(1f)) {
                        EmojiText(session.account.name, session.account.emojis, style = MaterialTheme.typography.titleMedium)
                        val address = session.account.acct
                        Text(
                            if ('@' in address) "@$address" else "@$address@${session.instance}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (active) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { removal = session }) {
                    Icon(Icons.Default.Delete, tr("mastodon_remove_account"), Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
        item {
            Button(
                onClick = { ui.navigate(Screen.Instance) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("mastodon_add_account"))
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
    var qr by remember(id) { mutableStateOf(false) }
    var relationshipBusy by remember(id) { mutableStateOf(false) }
    var moderationMenu by remember(id) { mutableStateOf(false) }
    fun changeRelationship(action: String) {
        ui.requireLogin {
            if (!relationshipBusy) {
                relationshipBusy = true
                ui.run {
                    try {
                        relationship = ui.api.relationshipAction(id, action)
                    } finally {
                        relationshipBusy = false
                    }
                }
            }
        }
    }
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
        ProfileTimeline(
            ui,
            person,
            header = {
                Column {
                    val self = ui.me?.id == id
                    val rel = relationship
                    ProfileMasthead(
                        account = person,
                        label = tr(if (self) "mastodon_edit_profile"
                            else if (rel?.requested == true) "mastodon_follow_requested"
                            else if (rel?.following == true) "mastodon_following" else "mastodon_follow"),
                        busy = !self && relationshipBusy,
                        shareEnabled = safeWebUrl(person.url) != null,
                        onPrimary = {
                            if (self) ui.navigate(Screen.EditProfile)
                            else changeRelationship(if (rel?.following == true || rel?.requested == true) "unfollow" else "follow")
                        },
                        onQr = { qr = true },
                        onShare = { safeWebUrl(person.url)?.let(ui.platform::share) },
                        overflow = if (self || ui.me == null) null else {
                            {
                                Box {
                                    IconButton(onClick = { moderationMenu = true }, enabled = !relationshipBusy,
                                        modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.MoreVert, tr("mastodon_more_actions"))
                                    }
                                    DropdownMenu(expanded = moderationMenu, onDismissRequest = { moderationMenu = false }) {
                                        DropdownMenuItem(text = { Text(tr(if (rel?.muting == true) "mastodon_unmute" else "mastodon_mute")) },
                                            onClick = {
                                                moderationMenu = false
                                                changeRelationship(if (rel?.muting == true) "unmute" else "mute")
                                            })
                                        DropdownMenuItem(text = { Text(tr(if (rel?.blocking == true) "mastodon_unblock" else "mastodon_block")) },
                                            onClick = {
                                                moderationMenu = false
                                                changeRelationship(if (rel?.blocking == true) "unblock" else "block")
                                            })
                                        DropdownMenuItem(text = { Text(tr("mastodon_report")) }, onClick = {
                                            moderationMenu = false
                                            report = true
                                        })
                                    }
                                }
                            }
                        },
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 4.dp)) {
                        Column(
                            Modifier.clickable(
                                enabled = safeWebUrl(person.url) != null,
                                onClickLabel = tr("mastodon_open_in_browser"),
                                onClick = { ui.open(person.url) },
                            ),
                        ) {
                            Box(Modifier.semantics { heading() }) {
                                EmojiText(person.name, person.emojis,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Text("@${person.acct}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!self && rel?.followedBy == true) {
                            Text(tr("mastodon_follows_you"), Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (person.note.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            HtmlContent(ui, person.note, emojis = person.emojis)
                        }
                        FlowRow(
                            Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileStat(person.followersCount, tr("mastodon_followers_count_label")) {
                                ui.navigate(Screen.People(id, false))
                            }
                            ProfileStat(person.followingCount, tr("mastodon_following_count")) {
                                ui.navigate(Screen.People(id, true))
                            }
                            ProfileStat(person.statusesCount, tr("mastodon_posts"))
                        }
                        if (self) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                val favourites = tr("mastodon_button_favorite")
                                val bookmarks = tr("mastodon_bookmarks")
                                TextButton(onClick = { ui.navigate(Screen.Feed(Timeline.Favourites, favourites)) },
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                                    Icon(Icons.Default.FavoriteBorder, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(favourites, style = MaterialTheme.typography.labelLarge)
                                }
                                TextButton(onClick = { ui.navigate(Screen.Feed(Timeline.Bookmarks, bookmarks)) },
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                                    Icon(PostIcons.Bookmark, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(bookmarks, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            },
            about = {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (person.createdAt.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(tr("mastodon_joined"), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(person.createdAt.take(10), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    person.fields.forEach { field ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            EmojiText(
                                field.name + if (field.verifiedAt != null) " ✓" else "",
                                person.emojis,
                                style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                            HtmlContent(ui, field.value, emojis = person.emojis)
                        }
                    }
                    if (person.createdAt.isBlank() && person.fields.isEmpty() && !person.bot && !person.locked) Empty()
                    if (person.bot) Text(tr("profile_automated_account"))
                    if (person.locked) Text(tr("mastodon_profile_locked"))
                }
            },
        )
        if (qr) ProfileQr(ui, person) { qr = false }
    }
    val reportSent = tr("mastodon_report_sent")
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
                            ui.message = reportSent
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
@Composable
private fun ProfileStat(count: Int, label: String, onClick: (() -> Unit)? = null) {
    val numberColor = MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold)) {
            append(count.toString())
        }
        append(" ")
        append(label)
    }
    Box(
        (if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileMasthead(
    account: Account,
    label: String,
    busy: Boolean,
    shareEnabled: Boolean,
    onPrimary: () -> Unit,
    onQr: () -> Unit,
    onShare: () -> Unit,
    overflow: (@Composable () -> Unit)?,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val header = remember(account.header) {
        safeWebUrl(account.header)?.takeUnless {
            it.substringBefore('?').substringBefore('#').substringAfterLast('/') == "missing.png"
        }
    }
    val avatar: @Composable () -> Unit = {
        Box(Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(3.dp)) {
            AccountAvatar(account, 64.dp)
        }
    }
    val primary: @Composable (Modifier) -> Unit = { modifier ->
        Button(
            onClick = onPrimary,
            enabled = !busy,
            modifier = modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = style)
        }
    }
    val secondary: @Composable () -> Unit = {
        val border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
        OutlinedIconButton(onClick = onQr, enabled = shareEnabled, modifier = Modifier.size(48.dp), border = border) {
            Icon(ProfileQrIcon, "QR", Modifier.size(19.dp))
        }
        OutlinedIconButton(onClick = onShare, enabled = shareEnabled, modifier = Modifier.size(48.dp), border = border) {
            Icon(Icons.Default.Share, tr("mastodon_button_share"), Modifier.size(19.dp))
        }
        overflow?.invoke()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val coverHeight = if (header == null) 84.dp else (maxWidth / 3.5f).coerceIn(100.dp, 156.dp)
        val available = maxWidth - 36.dp
        val primaryWidth = with(density) { measurer.measure(AnnotatedString(label), style, softWrap = false).size.width.toDp() } +
            32.dp + if (busy) 22.dp else 0.dp
        val actionWidth = primaryWidth + 54.dp * (if (overflow == null) 2 else 3)
        val fitWithAvatar = available >= actionWidth + 82.dp && density.fontScale <= 1.3f
        val fitOnOneLine = available >= actionWidth && density.fontScale <= 1.3f
        Box(Modifier.fillMaxWidth().height(coverHeight)) {
            ProfileCoverPattern(Modifier.matchParentSize())
            if (header != null) AsyncImage(header, tr("mastodon_profile_header"),
                Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = coverHeight - 22.dp)) {
            if (fitWithAvatar) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    avatar()
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        primary(Modifier)
                        secondary()
                    }
                }
            } else {
                avatar()
                Spacer(Modifier.height(8.dp))
                if (fitOnOneLine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        primary(Modifier.weight(1f))
                        secondary()
                    }
                } else {
                    primary(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically) { secondary() }
                }
            }
        }
    }
}

@Composable
private fun ProfileCoverPattern(modifier: Modifier) {
    val paper = MaterialTheme.colorScheme.surfaceContainerLow
    val ink = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.21f)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.clipToBounds()) {
        drawRect(paper)
        val center = Offset(size.width * 0.71f, size.height * 1.36f)
        val spacing = size.height * 0.18f
        for (ring in 0..8) {
            drawCircle(ink, radius = size.height * 0.48f + ring * spacing, center = center,
                style = Stroke(width = 0.8.dp.toPx()))
        }
        val radius = size.height * 0.48f + 6 * spacing
        drawCircle(accent, radius = 4.dp.toPx(), center = center + Offset(radius * 0.8f, -radius * 0.6f))
    }
}

private val ProfileQrIcon = ImageVector.Builder("QR", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
        for ((x, y) in listOf(3f to 3f, 15f to 3f, 3f to 15f)) {
            moveTo(x, y)
            lineTo(x + 6f, y)
            lineTo(x + 6f, y + 6f)
            lineTo(x, y + 6f)
            close()
        }
        moveTo(15f, 14f)
        lineTo(15f, 18f)
        lineTo(18f, 18f)
        lineTo(18f, 21f)
        moveTo(20f, 14f)
        lineTo(21f, 14f)
        lineTo(21f, 18f)
        moveTo(14f, 21f)
        lineTo(15f, 21f)
    }
}.build()

internal class PeopleListState {
    val viewport = LazyListState()
    private val rows = mutableStateListOf<Account>()
    private val accountIds = mutableSetOf<String>()
    val people: List<Account> get() = rows
    var cursor by mutableStateOf<String?>(null)
        private set
    var exhausted by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val loadedCursors = mutableSetOf<String?>()

    suspend fun loadNext(fetch: suspend (String?) -> Page<Account>) {
        if (loading || exhausted) return
        loading = true
        error = null
        try {
            val page = fetch(cursor)
            rows.addAll(page.items.filter { accountIds.add(it.id) })
            loadedCursors.add(cursor)
            cursor = page.nextCursor
            exhausted = cursor == null || cursor in loadedCursors
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        } finally {
            loading = false
        }
    }
}

@Composable
fun PeopleScreen(ui: UiContext, route: Screen.People) {
    val state = route.list
    fun load() {
        ui.scope.launch {
            state.loadNext { cursor -> ui.api.people(route.id, route.following, cursor) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = state.viewport) {
        items(state.people, key = { it.id }) { AccountRow(it) { ui.navigate(Screen.Profile(it.id)) } }
        item(contentType = "pagination") {
            when {
                state.error != null -> ErrorNotice(state.error, ::load)
                state.exhausted -> if (state.people.isEmpty()) Empty()
                else -> {
                    LaunchedEffect(state, state.cursor) { load() }
                    Loading()
                }
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
