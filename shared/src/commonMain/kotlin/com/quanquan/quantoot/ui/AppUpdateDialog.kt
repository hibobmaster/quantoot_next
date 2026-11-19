package com.quanquan.quantoot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        is UpdateStep.Available -> tr("settings_update_available", "version" to activeStep.update.version)
        is UpdateStep.Ready -> tr("settings_version_label", "version" to activeStep.update.version)
        is UpdateStep.Installing -> tr("settings_update_install")
        else -> tr("settings_check_for_updates")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (activeStep) {
                    UpdateStep.Checking -> {
                        Text(tr("settings_update_checking"))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    UpdateStep.Current -> Text(tr("settings_update_current", "version" to updates.currentVersion))
                    is UpdateStep.Available -> {
                        Text(tr("settings_update_download_size", "size" to formatByteSize(activeStep.update.bytes)))
                        TextButton(onClick = { openRelease(activeStep.update.releaseUrl) }) {
                            Text(tr("settings_update_release_notes"))
                        }
                    }
                    is UpdateStep.Downloading -> {
                        Text(tr("settings_update_downloading", "percent" to percent.toString()))
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateStep.Ready -> Text(tr(if (canInstall) "settings_update_ready" else "settings_update_permission"))
                    is UpdateStep.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is UpdateStep.Failed -> Text(tr(activeStep.message))
                }
            }
        },
        confirmButton = {
            when (activeStep) {
                is UpdateStep.Available -> TextButton(onClick = { step = UpdateStep.Downloading(activeStep.update) }) {
                    Text(tr("settings_update_download"))
                }
                is UpdateStep.Ready -> TextButton(onClick = {
                    try {
                        if (updates.canInstall()) step = UpdateStep.Installing(activeStep.update)
                        else updates.openInstallSettings()
                    } catch (_: Exception) {
                        step = UpdateStep.Failed("settings_update_install_failed", activeStep)
                    }
                }) {
                    Text(tr(if (canInstall) "settings_update_install" else "settings_update_allow"))
                }
                is UpdateStep.Failed -> TextButton(onClick = { step = activeStep.retry }) { Text(tr("mastodon_retry")) }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(if (activeStep == UpdateStep.Current) "mastodon_close" else "mastodon_cancel"))
            }
        },
    )
}
