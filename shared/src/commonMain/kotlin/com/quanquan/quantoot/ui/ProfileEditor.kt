package com.quanquan.quantoot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.plainText
import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.model.ProfileField
import com.quanquan.quantoot.model.ProfileUpdate
import com.quanquan.quantoot.platform.PickedMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ProfileImage(val labelKey: String, val editKey: String) {
    Avatar("mastodon_profile_avatar", "mastodon_profile_edit_avatar"),
    Header("mastodon_profile_header", "mastodon_profile_edit_header"),
}

@Composable
internal fun EditProfileScreen(ui: UiContext) {
    val account = ui.me ?: return
    val initial = remember(account.id) { account }
    val initialBio = remember(initial) { initial.source?.note ?: plainText(initial.note) }
    val initialFields = initial.source?.fields ?: initial.fields
    var name by remember(initial) { mutableStateOf(initial.displayName) }
    var bio by remember(initial) { mutableStateOf(initialBio) }
    var locked by remember(initial) { mutableStateOf(initial.locked) }
    var fields by remember(initial) { mutableStateOf(initialFields) }
    var avatar by remember(initial) { mutableStateOf<PickedMedia?>(null) }
    var header by remember(initial) { mutableStateOf<PickedMedia?>(null) }
    var avatarUrl by remember(initial) {
        mutableStateOf(profileImageUrl(initial.avatar) ?: profileImageUrl(initial.avatarStatic))
    }
    val avatarPreviewUrl = avatarUrl?.let { profileImageUrl(initial.avatarStatic) ?: it }
    var headerUrl by remember(initial) { mutableStateOf(profileImageUrl(initial.header)) }
    var imageMenu by remember { mutableStateOf<ProfileImage?>(null) }
    var removeImage by remember { mutableStateOf<ProfileImage?>(null) }
    var discardChanges by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dirty = name != initial.displayName || bio != initialBio || locked != initial.locked ||
        fields != initialFields || avatar != null || header != null
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val updated = tr("mastodon_profile_updated")
    val failure = tr("mastodon_unknown_error")

    val requestBack by rememberUpdatedState<() -> Unit> {
        if (!busy) {
            if (dirty) {
                keyboard?.hide()
                discardChanges = true
            } else {
                ui.back()
            }
        }
    }
    DisposableEffect(ui) {
        val handler = { requestBack() }
        ui.beforeBack = handler
        onDispose { if (ui.beforeBack === handler) ui.beforeBack = null }
    }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        error = null
        busy = true
        keyboard?.hide()
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: failure
            } finally {
                busy = false
            }
        }
    }

    fun choose(image: ProfileImage) = perform {
        val selected = ui.platform.pickMedia(1).firstOrNull() ?: return@perform
        when (image) {
            ProfileImage.Avatar -> avatar = selected
            ProfileImage.Header -> header = selected
        }
    }

    fun clear(image: ProfileImage) {
        when (image) {
            ProfileImage.Avatar -> {
                avatar = null
                avatarUrl = null
            }
            ProfileImage.Header -> {
                header = null
                headerUrl = null
            }
        }
    }

    fun hasSavedImage(image: ProfileImage): Boolean =
        if (image == ProfileImage.Avatar) avatarUrl != null else headerUrl != null

    fun edit(image: ProfileImage) {
        val hasReplacement = if (image == ProfileImage.Avatar) avatar != null else header != null
        if (hasSavedImage(image) || hasReplacement) {
            keyboard?.hide()
            imageMenu = image
        } else {
            choose(image)
        }
    }

    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 620.dp).fillMaxSize()) {
            EditorHeader(
                tr("mastodon_edit_profile"),
                ui::requestBack,
                closeEnabled = !busy,
                closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
            ) {
                Button(
                    onClick = {
                        perform {
                            ui.store.updateAccount(ui.api.updateProfile(ProfileUpdate(name, bio, locked, fields, avatar, header)))
                            ui.revision++
                            ui.message = updated
                            ui.back()
                        }
                    },
                    enabled = dirty && !busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(tr("mastodon_save"))
                }
            }
            ErrorNotice(error)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp).testTag("profile-editor"),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ProfileImagePreview(
                    initial.copy(displayName = name, avatar = avatarPreviewUrl.orEmpty(), avatarStatic = ""),
                    avatar, header?.bytes ?: headerUrl, enabled = !busy, onEdit = ::edit,
                )
                ProfileEditorField(tr("mastodon_profile_display_name"), name, { name = it }, enabled = !busy, singleLine = true)
                ProfileEditorField(tr("mastodon_profile_bio"), bio, { bio = it }, enabled = !busy, minLines = 3, maxLines = 7)
                Toggle(
                    tr("mastodon_profile_locked"), locked,
                    description = tr("mastodon_profile_locked_description"), enabled = !busy,
                ) { locked = it }
                ProfileFieldsEditor(fields, enabled = !busy, onChange = { fields = it })
            }
        }
    }

    imageMenu?.let { image ->
        ProfileImageActions(
            image,
            onDismiss = { imageMenu = null },
            onReplace = {
                imageMenu = null
                choose(image)
            },
            onRemove = {
                imageMenu = null
                if (hasSavedImage(image)) removeImage = image else clear(image)
            },
        )
    }
    removeImage?.let { image ->
        AlertDialog(
            onDismissRequest = { removeImage = null },
            title = { Text(tr("mastodon_profile_remove_image")) },
            text = { Text(tr("mastodon_profile_remove_image_confirmation")) },
            confirmButton = {
                TextButton(onClick = {
                    removeImage = null
                    perform {
                        val result = ui.api.deleteProfileImage(image == ProfileImage.Avatar)
                        clear(image)
                        ui.store.updateAccount(result)
                        ui.revision++
                    }
                }) { Text(tr("mastodon_profile_remove_image"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removeImage = null }) { Text(tr("mastodon_cancel")) } },
        )
    }
    if (discardChanges) {
        AlertDialog(
            onDismissRequest = { discardChanges = false },
            title = { Text(tr("mastodon_profile_discard_changes")) },
            confirmButton = {
                TextButton(onClick = ui.back) { Text(tr("mastodon_discard"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { discardChanges = false }) { Text(tr("mastodon_cancel")) } },
        )
    }
}

@Composable
private fun ProfileImagePreview(
    account: Account,
    avatar: PickedMedia?,
    header: Any?,
    enabled: Boolean,
    onEdit: (ProfileImage) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val coverShape = MaterialTheme.shapes.large
    val avatarLabel = tr(ProfileImage.Avatar.editKey)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val coverHeight = (maxWidth / 3.2f).coerceIn(112.dp, 160.dp)
        Box(Modifier.fillMaxWidth().height(coverHeight).clip(coverShape).border(Dp.Hairline, colors.outlineVariant, coverShape)) {
            ProfileCoverPattern(Modifier.matchParentSize())
            if (header != null) {
                AsyncImage(header, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
            FilledIconButton(
                onClick = { onEdit(ProfileImage.Header) },
                enabled = enabled,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.surface, contentColor = colors.onSurface),
            ) {
                Icon(Icons.Default.Edit, tr(ProfileImage.Header.editKey), Modifier.size(19.dp))
            }
        }
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = coverHeight - 28.dp)
                .size(86.dp).clip(MaterialTheme.shapes.large)
                .clickable(enabled = enabled, role = Role.Button) { onEdit(ProfileImage.Avatar) }
                .semantics { contentDescription = avatarLabel },
        ) {
            Box(Modifier.size(80.dp).background(colors.surface, CircleShape).padding(4.dp)) {
                AccountAvatar(account, 72.dp)
                if (avatar != null) {
                    AsyncImage(
                        avatar.bytes, null, Modifier.size(72.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).size(30.dp)
                    .background(colors.surface, CircleShape).border(1.dp, colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(15.dp), tint = colors.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileImageActions(image: ProfileImage, onDismiss: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(tr(image.labelKey), Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge)
        ListItem(
            headlineContent = { Text(tr("mastodon_profile_replace_image")) },
            leadingContent = { Icon(Icons.Default.Edit, null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onReplace),
        )
        ListItem(
            headlineContent = { Text(tr("mastodon_profile_remove_image")) },
            leadingContent = { Icon(Icons.Default.Delete, null) },
            colors = ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.error,
                leadingIconColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.clickable(role = Role.Button, onClick = onRemove),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProfileEditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value, onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                disabledContainerColor = colors.surfaceContainerLow,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ProfileFieldsEditor(fields: List<ProfileField>, enabled: Boolean, onChange: (List<ProfileField>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(thickness = Dp.Hairline)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("mastodon_profile_custom_fields"), Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium)
            if (fields.size < 4) {
                TextButton(onClick = { onChange(fields + ProfileField()) }, enabled = enabled) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("mastodon_profile_add_field"))
                }
            }
        }
        fields.forEachIndexed { index, field ->
            OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            ProfileEditorField(tr("mastodon_profile_field_name"), field.name, { text ->
                                onChange(fields.toMutableList().apply { set(index, field.copy(name = text)) })
                            }, enabled, singleLine = true)
                        }
                        IconButton(onClick = { onChange(fields.toMutableList().apply { removeAt(index) }) }, enabled = enabled) {
                            Icon(Icons.Default.Delete, tr("mastodon_delete"), Modifier.size(18.dp))
                        }
                    }
                    ProfileEditorField(tr("mastodon_profile_field_value"), field.value, { text ->
                        onChange(fields.toMutableList().apply { set(index, field.copy(value = text)) })
                    }, enabled, maxLines = 3)
                }
            }
        }
    }
}
