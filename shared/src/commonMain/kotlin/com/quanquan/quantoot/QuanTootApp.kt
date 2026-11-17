package com.quanquan.quantoot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.auth.*
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.PlatformServices
import com.quanquan.quantoot.resources.*
import com.quanquan.quantoot.ui.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun QuanTootApp(platform: PlatformServices) {
    val store = remember(platform) { AccountStore(platform) }
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) { store.restore() }
    LaunchedEffect(store) { platform.oauthRedirects.collect { store.acceptCallback(it) } }
    CompositionLocalProvider(
        LocalStrings provides Strings(state.locale ?: "system", platform.languageTag)
    ) {
        QuanTheme {
            Surface(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().safeDrawingPadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                        when {
                            !state.restored -> Loading()
                            state.locale == null ->
                                Column {
                                    ErrorNotice(state.error) { scope.launch { store.restore() } }
                                    Box(Modifier.weight(1f)) {
                                        LanguageChooser { scope.launch { store.setLocale(it) } }
                                    }
                                }
                            state.instance == null -> InstanceScreen(store, state, platform)
                            else ->
                                key(state.activeKey, state.instance, state.authorizationRevision) {
                                    val api = remember { store.client() }
                                    if (api == null) InstanceScreen(store, state, platform)
                                    else {
                                        DisposableEffect(api) { onDispose { api.close() } }
                                        AppShell(api, store, state, platform)
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageChooser(select: (String) -> Unit) {
    var language by remember { mutableStateOf("system") }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(Res.drawable.quantoot_logo), "QuanToot", Modifier.size(96.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                tr("onboarding_language_title"),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                tr("onboarding_language_description"),
                Modifier.padding(top = 12.dp, bottom = 28.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Choice(tr("settings_language"), language, languageOptions()) { language = it }
            Button(
                onClick = { select(language) },
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(tr("onboarding_continue"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppShell(
    api: com.quanquan.quantoot.data.MastodonApi,
    store: AccountStore,
    state: SessionState,
    platform: PlatformServices,
) {
    var tab by remember { mutableIntStateOf(0) }
    val routes = remember { mutableStateListOf<Screen>() }
    val scope = rememberCoroutineScope()
    val ui =
        remember(api) {
            UiContext(
                api,
                platform,
                store,
                state.activeSession?.account,
                scope,
                { routes.add(it) },
                { if (routes.isNotEmpty()) routes.removeLast() },
            )
        }
    SideEffect { ui.me = state.activeSession?.account }
    var unread by remember { mutableIntStateOf(0) }
    val foreground by platform.foreground.collectAsState()
    LaunchedEffect(api, foreground) {
        if (ui.me != null && foreground) {
            ui.run { unread = api.unreadCount() }
            try {
                api.events().collect { event ->
                    if (event is StreamEvent.Notify) unread++
                    if (event is StreamEvent.Delete) ui.deleted.add(event.id)
                    ui.events.emit(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.error = e.message
            }
        }
    }
    BackHandler(enabled = routes.isNotEmpty()) { ui.requestBack() }
    val labels =
        listOf(
            tr("mastodon_home"),
            tr("mastodon_explore"),
            tr("mastodon_notifications"),
            tr("mastodon_profile"),
        )
    val icons =
        listOf(
            Icons.Default.Home,
            Icons.Default.Search,
            Icons.Default.Notifications,
            Icons.Default.Person,
        )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (routes.isEmpty()) labels[tab]
                        else
                            when (val screen = routes.last()) {
                                is Screen.Feed -> screen.title
                                is Screen.Thread -> tr("mastodon_posts")
                                is Screen.Profile -> tr("mastodon_profile")
                                is Screen.People ->
                                    tr(
                                        if (screen.following) "mastodon_following_count"
                                        else "mastodon_followers_count_label"
                                    )
                                is Screen.Compose ->
                                    tr(
                                        if (screen.edit == null) "mastodon_new_post"
                                        else "mastodon_edit_post"
                                    )
                                Screen.Settings -> tr("settings_title")
                                Screen.Accounts -> tr("mastodon_accounts")
                                Screen.Instance -> tr("mastodon_choose_instance")
                                Screen.EditProfile -> tr("mastodon_edit_profile")
                            }
                    )
                },
                navigationIcon = {
                    if (routes.isNotEmpty())
                        IconButton(onClick = ui::requestBack) {
                            Icon(Icons.Default.ArrowBack, tr("mastodon_close"))
                        }
                },
                actions = {
                    if (routes.lastOrNull() !is Screen.Compose)
                        IconButton(onClick = { ui.navigate(Screen.Accounts) }) {
                            Icon(Icons.Default.Person, tr("mastodon_accounts"))
                        }
                    if (routes.lastOrNull() !is Screen.Compose)
                        IconButton(onClick = { ui.navigate(Screen.Settings) }) {
                            Icon(Icons.Default.Settings, tr("settings_title"))
                        }
                },
            )
        },
        bottomBar = {
            if (routes.isEmpty())
                NavigationBar {
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = {
                                tab = i
                                if (i == 2) unread = 0
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (i == 2 && unread > 0) Badge { Text(unread.toString()) }
                                    }
                                ) {
                                    Icon(icons[i], label)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
        },
        floatingActionButton = {
            if (routes.isEmpty() || routes.last() !is Screen.Compose)
                FloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = { ui.requireLogin { ui.navigate(Screen.Compose()) } },
                ) {
                    Icon(Icons.Default.Add, tr("mastodon_new_post"))
                }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ErrorNotice(state.error) { store.clearError() }
            ErrorNotice(ui.error, ui::retry)
            ui.message?.let { text ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f).padding(12.dp))
                        TextButton(onClick = { ui.message = null }) { Text(tr("mastodon_close")) }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                val route = routes.lastOrNull()
                key(route, if (route == null) tab else -1) {
                    when (route) {
                        is Screen.Feed -> FeedScreen(ui, route.source)
                        is Screen.Thread -> ThreadScreen(ui, route.id)
                        is Screen.Profile -> ProfileScreen(ui, route.id)
                        is Screen.People -> PeopleScreen(ui, route.id, route.following)
                        is Screen.Compose -> ComposerScreen(ui, route)
                        Screen.Settings -> SettingsScreen(ui, state)
                        Screen.Accounts -> AccountsScreen(ui, state)
                        Screen.Instance -> InstanceScreen(store, state, platform)
                        Screen.EditProfile -> EditProfileScreen(ui)
                        null ->
                            when (tab) {
                                0 -> HomeScreen(ui)
                                1 -> DiscoveryScreen(ui)
                                2 -> if (ui.me != null) NotificationsScreen(ui) else LoginPrompt(ui)
                                else -> ui.me?.let { ProfileScreen(ui, it.id) } ?: LoginPrompt(ui)
                            }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginPrompt(ui: UiContext) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("mastodon_login_required_message"))
        Button(onClick = { ui.navigate(Screen.Instance) }) { Text(tr("mastodon_login")) }
    }
}
