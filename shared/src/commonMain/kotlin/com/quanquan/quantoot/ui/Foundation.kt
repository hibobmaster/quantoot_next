package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.auth.AccountStore
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.PlatformServices
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

val LocalStrings = staticCompositionLocalOf { Strings("en", "en") }

@Composable
fun tr(key: String, vararg args: Pair<String, String>) = LocalStrings.current.text(key, *args)

sealed interface Screen {
    data class Feed(val source: Timeline, val title: String) : Screen

    data class Thread(val id: String, val focusReplyId: String? = null) : Screen

    data class Profile(val id: String) : Screen

    data class People(val id: String, val following: Boolean) : Screen

    data class Compose(
        val reply: Status? = null,
        val edit: Status? = null,
        val quote: Status? = null,
    ) : Screen

    data object Settings : Screen

    data object Accounts : Screen

    data object Instance : Screen

    data object EditProfile : Screen
}

class UiContext(
    val api: MastodonApi,
    val platform: PlatformServices,
    val store: AccountStore,
    initialMe: Account?,
    val scope: CoroutineScope,
    val navigate: (Screen) -> Unit,
    val back: () -> Unit,
    private val finishPublishing: (Screen.Compose, Status) -> Unit,
) {
    var me by mutableStateOf(initialMe)
    var beforeBack: (() -> Unit)? = null

    fun requestBack() {
        beforeBack?.invoke() ?: back()
    }

    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var revision by mutableIntStateOf(0)
    internal val statuses = StatusProjection()
    val drafts = mutableMapOf<String, ComposerDraft>()
    private val eventBus = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 32)
    val events = eventBus.asSharedFlow()
    private var failedOperation: (suspend () -> Unit)? = null

    fun run(block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedOperation = block
            error = e.message ?: "Request failed"
        }
    }

    fun retry() {
        error = null
        failedOperation?.let {
            failedOperation = null
            run(it)
        }
    }

    fun requireLogin(block: () -> Unit) {
        if (me == null) navigate(Screen.Instance) else block()
    }

    fun open(raw: String?) {
        raw?.let(::safeWebUrl)?.let(platform::openUrl)
    }

    suspend fun ingest(event: StreamEvent) {
        statuses.apply(event)
        eventBus.emit(event)
    }

    suspend fun published(route: Screen.Compose, status: Status) {
        ingest(
            if (route.edit == null) StreamEvent.Created(StreamSource.LocalWrite, status)
            else StreamEvent.Edited(StreamSource.LocalWrite, status)
        )
        finishPublishing(route, status)
    }

}

internal fun navigateTo(routes: List<Screen>, screen: Screen): List<Screen> {
    if (screen is Screen.Thread) {
        val existing = routes.indexOfLast { it is Screen.Thread && it.id == screen.id }
        if (existing >= 0) {
            val previous = routes[existing] as Screen.Thread
            return routes.take(existing) + screen.copy(focusReplyId = screen.focusReplyId ?: previous.focusReplyId)
        }
    }
    return if (routes.lastOrNull() == screen) routes else routes + screen
}

internal fun routesAfterPublish(routes: List<Screen>, compose: Screen.Compose, status: Status): List<Screen> {
    val previous = if (routes.lastOrNull() is Screen.Compose) routes.dropLast(1) else routes
    val parent = compose.reply ?: return previous
    return navigateTo(previous, Screen.Thread(parent.id, focusReplyId = status.id))
}

@Composable
fun QuanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette =
        if (dark)
            darkColorScheme(
                primary = Color(0xFF8FCDBF),
                onPrimary = Color(0xFF0A3932),
                primaryContainer = Color(0xFF24564F),
                onPrimaryContainer = Color(0xFFB8E9DE),
                secondary = Color(0xFFA7C9C0),
                secondaryContainer = Color(0xFF304D47),
                onSecondaryContainer = Color(0xFFCCE8DD),
                surface = Color(0xFF111714),
                onSurface = Color(0xFFE8E8DF),
                onSurfaceVariant = Color(0xFFC5CAC4),
                surfaceContainer = Color(0xFF1D2420),
                surfaceContainerLow = Color(0xFF181E1B),
                surfaceContainerLowest = Color(0xFF0C110F),
                outlineVariant = Color(0xFF444C46),
            )
        else
            lightColorScheme(
                primary = Color(0xFF286A60),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD7E9E3),
                onPrimaryContainer = Color(0xFF123C36),
                secondary = Color(0xFF476F68),
                secondaryContainer = Color(0xFFDDE9E5),
                onSecondaryContainer = Color(0xFF294E47),
                surface = Color(0xFFF6F2E9),
                onSurface = Color(0xFF202522),
                onSurfaceVariant = Color(0xFF59615C),
                surfaceContainer = Color(0xFFEAE5DB),
                surfaceContainerLow = Color(0xFFF1EDE4),
                surfaceContainerLowest = Color(0xFFFFFCF6),
                outlineVariant = Color(0xFFC8C5BB),
            )
    MaterialTheme(
        colorScheme = palette,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

@Composable
fun ErrorNotice(error: String?, retry: (() -> Unit)? = null) {
    if (error != null)
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(error)
                if (retry != null) TextButton(onClick = retry) { Text(tr("mastodon_retry")) }
            }
        }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun Empty(label: String = tr("mastodon_empty_timeline")) {
    Text(label, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, role = Role.Switch, onValueChange = onChange)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f).padding(end = 16.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Choice(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second ?: value
    Surface(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(selectedLabel, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
    }
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            modifier = Modifier.semantics { paneTitle = label },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetMaxWidth = Dp.Unspecified,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                label,
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).selectableGroup(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(options, key = { it.first }) { (key, text) ->
                    val selected = key == value
                    Surface(
                        color =
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        expanded = false
                                        onChange(key)
                                    },
                                )
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun visibilityOptions() =
    listOf(
        "public" to tr("mastodon_public"),
        "unlisted" to tr("mastodon_unlisted"),
        "private" to tr("mastodon_followers_only"),
        "direct" to tr("mastodon_direct"),
    )

@Composable
fun quoteOptions() =
    listOf(
        "public" to tr("mastodon_quote_policy_public"),
        "followers" to tr("mastodon_quote_policy_followers"),
        "nobody" to tr("mastodon_quote_policy_nobody"),
    )

@Composable
fun languageOptions() =
    listOf(
        "system" to tr("settings_language_system"),
        "en" to "English",
        "zh" to "简体中文",
        "zh-TW" to "繁體中文",
    )
