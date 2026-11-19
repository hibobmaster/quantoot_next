package com.quanquan.quantoot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quanquan.quantoot.platform.AppUpdate
import com.quanquan.quantoot.platform.AppUpdates
import com.quanquan.quantoot.platform.InvalidUpdatePackage
import com.quanquan.quantoot.platform.NoCompatibleUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

private sealed interface UpdateStep {
    data object Checking : UpdateStep
    data object Current : UpdateStep
    data class Available(val update: AppUpdate) : UpdateStep
    data class Downloading(val update: AppUpdate) : UpdateStep
    data class Ready(val update: AppUpdate) : UpdateStep
    data class Installing(val update: AppUpdate) : UpdateStep
    data class Failed(val message: String, val retry: UpdateStep) : UpdateStep
}

@Composable
internal fun AppUpdateDialog(
    updates: AppUpdates,
    foreground: StateFlow<Boolean>,
    openRelease: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember(updates) { mutableStateOf<UpdateStep>(UpdateStep.Checking) }
    var percent by remember(updates) { mutableIntStateOf(0) }
    val activeStep = step
    LaunchedEffect(updates, activeStep) {
        try {
            when (activeStep) {
                UpdateStep.Checking -> step = updates.check()?.let { UpdateStep.Available(it) } ?: UpdateStep.Current
                is UpdateStep.Downloading -> {
                    percent = 0
                    updates.download(activeStep.update) { percent = it }
                    step = UpdateStep.Ready(activeStep.update)
                }
                is UpdateStep.Installing -> {
                    updates.install(activeStep.update)
                    step = UpdateStep.Ready(activeStep.update)
                }
                else -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = when (e) {
                is NoCompatibleUpdate -> "settings_update_no_compatible_apk"
                is InvalidUpdatePackage -> "settings_update_unverified"
                else -> when (activeStep) {
                    is UpdateStep.Downloading -> "settings_update_download_failed"
                    is UpdateStep.Installing -> "settings_update_install_failed"
                    else -> "settings_update_check_failed"
                }
            }
            val retry = if (activeStep is UpdateStep.Installing && e is InvalidUpdatePackage) {
                UpdateStep.Downloading(activeStep.update)
            } else activeStep
            step = UpdateStep.Failed(message, retry)
        }
    }

    val isForeground by foreground.collectAsState()
    val canInstall = remember(updates, activeStep, isForeground) {
        activeStep is UpdateStep.Ready && updates.canInstall()
    }
    val title = when (activeStep) {
        UpdateStep.Checking -> tr("settings_update_checking")
        UpdateStep.Current -> tr("settings_update_current", "version" to updates.currentVersion)
        is UpdateStep.Available -> tr("settings_update_new_version")
        is UpdateStep.Downloading -> tr("settings_update_downloading_title")
        is UpdateStep.Ready -> tr("settings_update_ready_title")
        is UpdateStep.Installing -> tr("settings_update_install")
        is UpdateStep.Failed -> tr("settings_check_for_updates")
    }
    val icon = when (activeStep) {
        UpdateStep.Current, is UpdateStep.Ready -> Icons.Default.Check
        is UpdateStep.Available, is UpdateStep.Downloading -> Icons.Default.KeyboardArrowDown
        is UpdateStep.Failed -> Icons.Default.Info
        else -> Icons.Default.Refresh
    }
    val primaryAction: Pair<String, () -> Unit>? = when (activeStep) {
        is UpdateStep.Available -> tr("settings_update_download") to { step = UpdateStep.Downloading(activeStep.update) }
        is UpdateStep.Ready -> tr(if (canInstall) "settings_update_install" else "settings_update_allow") to {
            try {
                if (updates.canInstall()) step = UpdateStep.Installing(activeStep.update)
                else updates.openInstallSettings()
            } catch (_: Exception) {
                step = UpdateStep.Failed("settings_update_install_failed", activeStep)
            }
        }
        is UpdateStep.Failed -> tr("mastodon_retry") to { step = activeStep.retry }
        else -> null
    }
    val colors = MaterialTheme.colorScheme
    val failed = activeStep is UpdateStep.Failed
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (failed) colors.errorContainer else colors.surfaceContainerHigh,
                    ) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(22.dp),
                                tint = if (failed) colors.onErrorContainer else colors.primary)
                        }
                    }
                    Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                }
                when (activeStep) {
                    UpdateStep.Checking, is UpdateStep.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    UpdateStep.Current -> Unit
                    is UpdateStep.Available -> {
                        UpdateVersionSummary(updates.currentVersion, activeStep.update)
                        UpdateReleaseNotes(activeStep.update.releaseNotes) { openRelease(activeStep.update.releaseUrl) }
                    }
                    is UpdateStep.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("settings_version_label", "version" to activeStep.update.version),
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("$percent%", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                            }
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is UpdateStep.Ready -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tr("settings_version_label", "version" to activeStep.update.version),
                                style = MaterialTheme.typography.titleMedium)
                            Text(tr(if (canInstall) "settings_update_ready" else "settings_update_permission"),
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    is UpdateStep.Failed -> Text(tr(activeStep.message),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                val dismissLabel = tr(if (activeStep == UpdateStep.Current) "mastodon_close" else "mastodon_cancel")
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (primaryAction == null) {
                        TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    } else {
                        val padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).fillMaxHeight(),
                            contentPadding = padding,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurfaceVariant),
                        ) {
                            Text(dismissLabel, textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = primaryAction.second,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).fillMaxHeight(),
                            contentPadding = padding,
                        ) {
                            Text(primaryAction.first, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateReleaseNotes(notes: String, openRelease: () -> Unit) {
    if (notes.isBlank()) {
        TextButton(onClick = openRelease) { Text(tr("settings_update_release_notes")) }
        return
    }
    var expanded by remember(notes) { mutableStateOf(false) }
    var canExpand by remember(notes) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                tr("settings_update_release_notes"),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = openRelease) { Text(tr("settings_update_view_release")) }
        }
        val viewport = if (expanded) Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()) else Modifier
        Text(
            notes,
            Modifier.fillMaxWidth().then(viewport),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) canExpand = it.didOverflowHeight },
        )
        if (canExpand || expanded) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.Start)) {
                Text(tr(if (expanded) "settings_update_collapse_notes" else "settings_update_expand_notes"))
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun UpdateVersionSummary(current: String, update: AppUpdate) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("v$current", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp), tint = colors.onSurfaceVariant)
                Text("v${update.version}", style = MaterialTheme.typography.titleMedium, color = colors.primary)
            }
            Text(tr("settings_update_download_size", "size" to formatByteSize(update.bytes)),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
