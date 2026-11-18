package com.quanquan.quantoot

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
import com.quanquan.quantoot.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

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
                    Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                        when {
                            !state.restored -> Loading()
                            state.locale == null ->
                                Column {
                                    ErrorNotice(state.error) { scope.launch { store.restore() } }
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                        LanguageChooser { scope.launch { store.setLocale(it) } }
                                    }
                                }
                            state.instance == null -> InstanceScreen(store, state)
                            else ->
                                key(state.activeKey, state.instance, state.authorizationRevision) {
                                    val api = remember { store.client() }
                                    if (api == null) InstanceScreen(store, state)
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
    val deviceLanguage = LocalStrings.current.systemLanguage
    CompositionLocalProvider(LocalStrings provides remember(language, deviceLanguage) { Strings(language, deviceLanguage) }) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = 24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BrandMark(Modifier.size(52.dp))
                    Wordmark()
                }
                Spacer(Modifier.height(44.dp))
                Text(tr("onboarding_language_title"), style = MaterialTheme.typography.headlineLarge)
                Text(tr("onboarding_language_description"), Modifier.padding(top = 8.dp, bottom = 24.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup()) {
                    languageOptions().forEach { (value, name) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(language == value, role = Role.RadioButton,
                                onClick = { language = value }).heightIn(min = 57.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            RadioButton(selected = language == value, onClick = null)
                        }
                        HorizontalDivider(thickness = Dp.Hairline)
                    }
                }
            }
            Button(onClick = { select(language) }, modifier = Modifier.fillMaxWidth()
                .padding(vertical = 16.dp).heightIn(min = 52.dp)) {
                Text(tr("onboarding_continue"))
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
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
    var tab by remember { mutableStateOf(MainTab.Home) }
    val homeReselected = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    var homeTimeline by remember { mutableStateOf(HomeTimeline.initial(state.activeSession != null)) }
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
                { screen ->
                    val next = navigateTo(routes.toList(), screen)
                    routes.clear()
                    routes.addAll(next)
                },
                { if (routes.isNotEmpty()) routes.removeLast() },
                { compose, status ->
                    val next = routesAfterPublish(routes.toList(), compose, status)
                    routes.clear()
                    routes.addAll(next)
                },
            )
        }
    SideEffect { ui.me = state.activeSession?.account }
    var unread by remember { mutableIntStateOf(0) }
    val foreground by platform.foreground.collectAsState()
    LaunchedEffect(api, foreground) {
        if (ui.me != null && foreground) {
            ui.run { unread = api.unreadCount() }
            try {
                api.events(StreamSource.User).collect { event ->
                    if (event is StreamEvent.Notify && (tab != MainTab.Notifications || routes.isNotEmpty())) unread++
                    ui.ingest(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.error = e.message
            }
        }
    }
    BackHandler(enabled = routes.isNotEmpty()) { ui.requestBack() }
    val discovery = remember(ui) { DiscoveryState(ui) }
    val route = routes.lastOrNull()
    val snackbar = remember { SnackbarHostState() }
    val message = ui.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Short)
            if (ui.message == message) ui.message = null
        }
    }
    val select: (MainTab) -> Unit = { next ->
        if (routes.isEmpty() && tab == MainTab.Home && next == tab) homeReselected.tryEmit(Unit)
        routes.clear()
        tab = next
        if (next == MainTab.Notifications) unread = 0
    }
    val compose: () -> Unit = { ui.requireLogin { ui.navigate(Screen.Compose()) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 840.dp && route !is Screen.Compose && route != Screen.EditProfile
        Row(Modifier.fillMaxSize()) {
            if (rail) DestinationRail(tab, unread, select, compose)
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = { if (route !is Screen.Compose) ShellHeader(ui, tab, route, rail) },
                bottomBar = { if (!rail && route == null) DestinationBar(tab, unread, select) },
                floatingActionButton = {
                    if (!rail && ((route == null && tab != MainTab.Explore) || route is Screen.Feed || route is Screen.Profile))
                        NewPostButton(compose)
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (route != Screen.Instance) ErrorNotice(state.error) { store.clearError() }
                    ErrorNotice(ui.error, ui::retry)
                    Box(Modifier.weight(1f)) {
                        key(route, if (route == null) tab else null) {
                            when (route) {
                                is Screen.Feed -> FeedScreen(ui, route.source)
                                is Screen.Thread -> ThreadScreen(ui, route.id, route.focusReplyId)
                                is Screen.Profile -> ProfileScreen(ui, route.id)
                                is Screen.People -> PeopleScreen(ui, route.id, route.following)
                                is Screen.Compose -> ComposerScreen(ui, route)
                                is Screen.Settings -> SettingsScreen(ui, state, route.section)
                                Screen.Accounts -> AccountsScreen(ui, state)
                                Screen.Instance -> InstanceScreen(store, state, showHeading = false) {
                                    routes.clear()
                                    homeTimeline = HomeTimeline.Local
                                    tab = MainTab.Home
                                }
                                Screen.EditProfile -> EditProfileScreen(ui)
                                null -> when (tab) {
                                    MainTab.Home -> HomeScreen(ui, homeTimeline, homeReselected) { homeTimeline = it }
                                    MainTab.Explore -> DiscoveryScreen(ui, discovery)
                                    MainTab.Notifications -> if (ui.me != null) NotificationsScreen(ui)
                                        else LoginPrompt(ui, "mastodon_notifications_login_title", "mastodon_notifications_login_message", Icons.Default.Notifications)
                                    MainTab.Profile -> ui.me?.let { ProfileScreen(ui, it.id) }
                                        ?: LoginPrompt(ui, "mastodon_profile_login_title", "mastodon_profile_login_message", Icons.Default.Person)
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
fun LoginPrompt(
    ui: UiContext,
    titleKey: String = "mastodon_login_title_activity",
    messageKey: String = "mastodon_login_required_message",
    icon: ImageVector = Icons.Default.Person,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Icon(icon, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(tr(titleKey), Modifier.padding(top = 18.dp), style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center)
        Text(tr(messageKey), Modifier.widthIn(max = 360.dp).padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = { ui.navigate(Screen.Instance) }, modifier = Modifier.padding(top = 20.dp)) {
            Text(tr("mastodon_login"))
        }
    }
}
