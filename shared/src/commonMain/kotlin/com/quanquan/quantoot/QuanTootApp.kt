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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.auth.*
import com.quanquan.quantoot.data.GuestTimelineAccess
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.PlatformServices
import com.quanquan.quantoot.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun QuanTootApp(platform: PlatformServices, window: AdaptiveWindow = AdaptiveWindow()) {
    val store = remember(platform) { AccountStore(platform) }
    QuanTootApp(store, window)
}

@Composable
internal fun QuanTootApp(store: AccountStore, window: AdaptiveWindow) {
    val platform = store.platform
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) { store.restore() }
    LaunchedEffect(store) { platform.oauthRedirects.collect { store.acceptCallback(it) } }
    CompositionLocalProvider(
        LocalStrings provides Strings(state.locale ?: "system", platform.languageTag),
        LocalAdaptiveWindow provides window,
    ) {
        QuanTheme {
            Surface(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().safeDrawingPadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        when {
                            !state.restored -> Loading()
                            state.locale == null -> OnboardingPane {
                                Column {
                                    ErrorNotice(state.error) { scope.launch { store.restore() } }
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                        LanguageChooser { scope.launch { store.setLocale(it) } }
                                    }
                                }
                            }
                            state.instance == null -> OnboardingPane { InstanceScreen(store, state) }
                            else ->
                                key(state.activeKey, state.instance, state.authorizationRevision) {
                                    val api = remember { store.client() }
                                    if (api == null) OnboardingPane { InstanceScreen(store, state) }
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
private fun OnboardingPane(content: @Composable () -> Unit) {
    AdaptiveWorkspace(wantsSecondary = false, navigation = false, singleMaxWidth = 520f) { layout ->
        WorkspacePane(layout.primary) { content() }
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
    val signedIn = state.activeSession != null
    val publicTimelines by produceState(if (signedIn) HomeTimeline.publicFeeds else emptyList(), api) {
        if (!signedIn) {
            fun GuestTimelineAccess.timelines() = buildList {
                if (local) add(HomeTimeline.Local)
                if (federated) add(HomeTimeline.Federated)
            }
            val cached = GuestTimelineAccess.restore(platform.preferences, api.instance)
            if (cached != null) value = cached.timelines()
            val refreshed = GuestTimelineAccess.refresh(platform.preferences, api, api.instance != DEFAULT_INSTANCE, cached)
            if (refreshed != null) value = refreshed.timelines()
        }
    }
    val initialTab = if (signedIn) MainTab.Home else MainTab.Explore
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val homeReselected = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    var homeSelection by rememberSaveable { mutableStateOf<HomeTimeline?>(null) }
    val homeTimeline = homeSelection?.takeIf { it == HomeTimeline.Home || it in publicTimelines }
        ?: HomeTimeline.initial(signedIn, publicTimelines)
    val routes = rememberSaveable(saver = screenStackSaver) { mutableStateListOf<Screen>() }
    val scope = rememberCoroutineScope()
    val ui =
        remember(api) {
            UiContext(
                api,
                platform,
                store,
                state.activeSession?.account,
                state.activeKey,
                scope,
                { screen ->
                    val next = navigateTo(routes.toList(), screen)
                    routes.clear()
                    routes.addAll(next)
                },
                { if (routes.isNotEmpty()) routes.removeAt(routes.lastIndex) },
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
            launch {
                try {
                    unread = api.unreadCount()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep the current badge when this background request fails.
                }
            }
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
    val editing = route is Screen.Compose || route == Screen.EditProfile
    val readingDetail = route is Screen.Feed || route is Screen.Thread || route is Screen.Profile || route is Screen.People
    val showExploreAlongsideHome = route == null && tab == MainTab.Home && signedIn
    val native = LocalNativeNavigation.current
    DisposableEffect(native) { onDispose { native?.clear() } }
    AdaptiveWorkspace(readingDetail || showExploreAlongsideHome, navigation = !editing && native == null) { layout ->
        val rail = layout.rail != null
        val splitDetail = readingDetail && layout.secondary != null
        if (native != null) {
            val split = layout.secondary != null
            val backTitle = tr("mastodon_close")
            val composeTitle = tr("mastodon_new_post")
            val accountsTitle = tr("mastodon_accounts")
            val settingsTitle = tr("settings_title")
            val navigationState = NativeNavigationState(
                destinations = MainTab.entries.map { NativeDestination(it.name, tr(it.titleKey)) },
                selected = tab.name,
                unread = unread,
                title = if (split) "" else screenTitle(tab, route),
                subtitle = api.instance.takeIf { !split && route == null && tab == MainTab.Home },
                showsTabs = route == null || readingDetail,
                editing = editing,
                actions = buildList {
                    if (route != null) {
                        add(NativeToolbarAction(if (editing || splitDetail) NativeAction.Close else NativeAction.Back, backTitle))
                    }
                    if (!editing) {
                        if (route == null || route is Screen.Feed || route is Screen.Profile) {
                            add(NativeToolbarAction(NativeAction.Compose, composeTitle))
                        }
                        if (route != Screen.Accounts) add(NativeToolbarAction(NativeAction.Accounts, accountsTitle))
                        if (route !is Screen.Settings) add(NativeToolbarAction(NativeAction.Settings, settingsTitle))
                    }
                },
            )
            SideEffect {
                native.update(navigationState, { id -> MainTab.entries.firstOrNull { it.name == id }?.let(select) }) {
                    when (it) {
                        NativeAction.Back, NativeAction.Close -> ui.requestBack()
                        NativeAction.Compose -> compose()
                        NativeAction.Accounts -> ui.navigate(Screen.Accounts)
                        NativeAction.Settings -> ui.navigate(Screen.Settings())
                        NativeAction.Confirm -> Unit
                    }
                }
            }
        }
        layout.rail?.let { bounds ->
            WorkspacePane(bounds) { DestinationRail(tab, unread, select, compose) }
        }
        if (route == null || splitDetail) {
            key("timeline-pane") {
                WorkspacePane(layout.primary) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        topBar = {
                            if (native == null || layout.secondary != null) ShellHeader(ui, tab, null, rail, nativeBar = native != null)
                        },
                        bottomBar = { if (native == null && !rail) DestinationBar(tab, unread, select) },
                        snackbarHost = { if (route == null) SnackbarHost(snackbar) },
                        floatingActionButton = { if (native == null && !rail && tab != MainTab.Explore) NewPostButton(compose) },
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            if (route == null) {
                                ErrorNotice(state.error) { store.clearError() }
                                ErrorNotice(ui.error, ui::retry)
                            }
                            Box(Modifier.weight(1f)) {
                                key(tab) {
                                    when (tab) {
                                    MainTab.Home -> HomeScreen(ui, homeTimeline, homeReselected, publicTimelines) { homeSelection = it }
                                    MainTab.Explore -> DiscoveryScreen(ui, discovery, publicTimelines)
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
        if (route != null) {
            // This slot stays composed as the same route moves between full width and the detail pane.
            key("detail-pane") {
                WorkspacePane(layout.secondary ?: layout.primary) {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0),
                        snackbarHost = { SnackbarHost(snackbar) },
                        topBar = {
                            if (!editing && (native == null || splitDetail)) {
                                ShellHeader(ui, tab, route, rail, detailPane = splitDetail, nativeBar = native != null)
                            }
                        },
                        floatingActionButton = {
                            if (native == null && !rail && !splitDetail && (route is Screen.Feed || route is Screen.Profile))
                                NewPostButton(compose)
                        },
                    ) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            if (route !is Screen.Instance) ErrorNotice(state.error) { store.clearError() }
                            ErrorNotice(ui.error, ui::retry)
                            Box(Modifier.weight(1f)) {
                                key(route) {
                                    when (route) {
                                        is Screen.Feed -> FeedScreen(ui, route.source, visit = route.visit)
                                        is Screen.Thread -> ThreadScreen(ui, route.id, route.focusReplyId)
                                        is Screen.Profile -> ProfileScreen(ui, route.id)
                                        is Screen.People -> PeopleScreen(ui, route)
                                        is Screen.Compose -> ComposerScreen(ui, route)
                                        is Screen.Settings -> SettingsScreen(ui, state, route.section)
                                        Screen.Accounts -> AccountsScreen(ui, state)
                                        is Screen.Instance -> InstanceScreen(
                                            store, state, showHeading = false,
                                            prefillCurrentInstance = route.prefillCurrentInstance,
                                        ) {
                                            routes.clear()
                                            homeSelection = null
                                            tab = initialTab
                                        }
                                        Screen.EditProfile -> EditProfileScreen(ui)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (layout.secondary != null) {
            WorkspacePane(layout.secondary) {
                Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
                    ShellHeader(ui, MainTab.Explore, null, rail, detailPane = true, nativeBar = native != null)
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        DiscoveryScreen(ui, discovery, publicTimelines)
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
        Button(onClick = { ui.navigate(Screen.Instance()) }, modifier = Modifier.padding(top = 20.dp)) {
            Text(tr("mastodon_login"))
        }
    }
}
