package com.quanquan.quantoot.ui

import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.quanquan.quantoot.platform.PlatformServices

data class NativeDestination(val id: String, val title: String)

enum class NativeAction { Back, Close, Compose, Accounts, Settings, Confirm }

data class NativeToolbarAction(val action: NativeAction, val title: String, val enabled: Boolean = true)

data class NativeNavigationState(
    val destinations: List<NativeDestination>,
    val selected: String,
    val unread: Int,
    val title: String,
    val subtitle: String?,
    val showsTabs: Boolean,
    val editing: Boolean,
    val actions: List<NativeToolbarAction>,
)

class NativeNavigation(private val onChange: (NativeNavigationState?) -> Unit) {
    private var state: NativeNavigationState? = null
    private var selectDestination: ((String) -> Unit)? = null
    private var performAction: ((NativeAction) -> Unit)? = null
    private var editorActions: List<NativeToolbarAction> = emptyList()
    private var performEditorAction: ((NativeAction) -> Unit)? = null
    private var displayed: NativeNavigationState? = null

    internal fun update(
        state: NativeNavigationState,
        select: (String) -> Unit,
        perform: (NativeAction) -> Unit,
    ) {
        this.state = state
        selectDestination = select
        performAction = perform
        publish()
    }

    internal fun edit(actions: List<NativeToolbarAction>, perform: ((NativeAction) -> Unit)?) {
        editorActions = actions
        performEditorAction = perform
        publish()
    }

    internal fun clear() {
        state = null
        selectDestination = null
        performAction = null
        editorActions = emptyList()
        performEditorAction = null
        publish()
    }

    fun select(id: String) {
        if (state?.showsTabs == true) selectDestination?.invoke(id)
    }

    fun perform(action: NativeAction) {
        if (displayed?.actions?.any { it.action == action && it.enabled } != true) return
        if (state?.editing == true && performEditorAction != null) performEditorAction?.invoke(action)
        else performAction?.invoke(action)
    }

    private fun publish() {
        val current = state?.let { if (it.editing && editorActions.isNotEmpty()) it.copy(actions = editorActions) else it }
        if (displayed == current) return
        displayed = current
        onChange(current)
    }
}

internal val LocalNativeNavigation = compositionLocalOf<NativeNavigation?> { null }

@Composable
internal fun NativeEditorActions(
    close: () -> Unit,
    closeEnabled: Boolean,
    confirmTitle: String,
    confirmEnabled: Boolean,
    confirm: () -> Unit,
): Boolean {
    val native = LocalNativeNavigation.current ?: return false
    val closeTitle = tr("mastodon_close")
    SideEffect {
        native.edit(
            listOf(NativeToolbarAction(NativeAction.Close, closeTitle, closeEnabled),
                NativeToolbarAction(NativeAction.Confirm, confirmTitle, confirmEnabled)),
        ) { action -> if (action == NativeAction.Confirm) confirm() else close() }
    }
    DisposableEffect(native) { onDispose { native.edit(emptyList(), null) } }
    return true
}

@Composable
internal fun ConfirmDiscard(
    platform: PlatformServices,
    title: String,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    val discard = tr("mastodon_discard")
    val cancel = tr("mastodon_cancel")
    val dismiss by rememberUpdatedState(onDismiss)
    val confirm by rememberUpdatedState(onDiscard)
    val native = LocalNativeNavigation.current
    var fallback by remember(native) { mutableStateOf(native == null) }
    if (fallback) {
        AlertDialog(
            onDismissRequest = onDismiss, title = { Text(title) },
            confirmButton = { TextButton(onClick = onDiscard) { Text(discard, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(cancel) } },
        )
    } else {
        LaunchedEffect(platform, title) {
            when (platform.confirmDiscard(title, discard, cancel)) {
                true -> confirm()
                false -> dismiss()
                null -> fallback = true
            }
        }
    }
}
