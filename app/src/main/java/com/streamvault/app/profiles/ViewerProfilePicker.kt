package com.streamvault.app.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamvault.app.R
import com.streamvault.core.ui.components.TvOutlinedTextField
import com.streamvault.core.ui.components.dialogs.PremiumDialog
import com.streamvault.core.ui.components.dialogs.PremiumDialogFooterButton
import com.streamvault.core.ui.design.BrowseColors
import com.streamvault.core.ui.design.BrowseFocusSpec
import com.streamvault.core.ui.design.BrowseShapes
import com.streamvault.core.ui.interaction.TvButton
import com.streamvault.domain.repository.ViewerProfile
import com.streamvault.domain.repository.ViewerProfileErrors
import com.streamvault.domain.repository.ViewerProfileState

private val ProfileColors = listOf(
    Color(0xFF26A69A), Color(0xFFEF8A3C), Color(0xFF8E6CEF),
    Color(0xFFE5619A), Color(0xFF4A8FE7), Color(0xFF6DBE4B)
)

fun profileColor(colorIndex: Int): Color = ProfileColors[Math.floorMod(colorIndex, ProfileColors.size)]

/** A round avatar with the profile's first letter. */
@Composable
fun ProfileAvatar(name: String, colorIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).background(profileColor(colorIndex), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

private sealed interface ProfileEditor {
    data object New : ProfileEditor
    data class Existing(val profile: ViewerProfile) : ProfileEditor
}

/**
 * "Who's watching?": picks the profile for the active provider, and adds, renames or deletes
 * profiles ("Manage profiles"). Back closes it and keeps the current profile.
 */
@Composable
fun ViewerProfilePicker(
    state: ViewerProfileState,
    busy: Boolean,
    error: String?,
    onPick: (ViewerProfile) -> Unit,
    onAdd: (name: String, primaryName: String, onDone: () -> Unit) -> Unit,
    onRename: (ViewerProfile, String, onDone: () -> Unit) -> Unit,
    onDelete: (ViewerProfile, onDone: () -> Unit) -> Unit,
    onClearError: () -> Unit,
    onClose: () -> Unit
) {
    val primaryName = stringResource(R.string.profiles_default_name)
    // With no profiles yet, show the original data as one profile so there is something to pick.
    val shown = state.profiles.ifEmpty {
        listOf(ViewerProfile(id = 0L, providerId = state.providerId ?: 0L, name = primaryName, colorIndex = 0, isPrimary = true, isActive = true))
    }
    var managing by remember { mutableStateOf(false) }
    // Nothing left to manage once the last extra profile is deleted.
    LaunchedEffect(state.profiles.isEmpty()) { if (state.profiles.isEmpty()) managing = false }
    var editor by remember { mutableStateOf<ProfileEditor?>(null) }
    val firstFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(enabled = !busy) { if (managing) managing = false else onClose() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BrowseColors.Canvas.copy(alpha = 0.97f))
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(if (managing) R.string.profiles_manage else R.string.profiles_who_is_watching),
                style = MaterialTheme.typography.displaySmall,
                color = BrowseColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.profiles_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = BrowseColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(640.dp)
            )
            Spacer(Modifier.height(40.dp))
            // Padding leaves room for the focused tile's outline and zoom.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                items(shown, key = { it.id }) { profile ->
                    ProfileTile(
                        label = profile.name,
                        selected = profile.isActive && !managing,
                        editing = managing,
                        modifier = if (profile.isActive) Modifier.focusRequester(firstFocus) else Modifier,
                        avatar = { ProfileAvatar(profile.name, profile.colorIndex, 112.dp) },
                        onClick = {
                            when {
                                busy -> Unit
                                managing && profile.id != 0L -> editor = ProfileEditor.Existing(profile)
                                managing -> Unit
                                else -> onPick(profile)
                            }
                        }
                    )
                }
                item(key = "add") {
                    ProfileTile(
                        label = stringResource(R.string.profiles_add),
                        selected = false,
                        editing = false,
                        avatar = {
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .background(BrowseColors.SurfaceEmphasis, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = BrowseColors.TextPrimary, modifier = Modifier.size(48.dp))
                            }
                        },
                        onClick = { if (!busy) editor = ProfileEditor.New }
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    Text(stringResource(R.string.profiles_switching), color = BrowseColors.TextSecondary)
                } else if (state.profiles.isNotEmpty()) {
                    TvButton(onClick = { managing = !managing }) {
                        Text(stringResource(if (managing) R.string.profiles_done else R.string.profiles_manage))
                    }
                }
            }
            if (error != null && editor == null) {
                Text(profileErrorText(error), color = Color(0xFFF0A898), style = MaterialTheme.typography.bodyMedium)
            }
        }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

        editor?.let { current ->
            ProfileEditorDialog(
                editor = current,
                busy = busy,
                error = error,
                onSave = { name ->
                    when (current) {
                        ProfileEditor.New -> onAdd(name, primaryName) { editor = null }
                        is ProfileEditor.Existing -> onRename(current.profile, name) { editor = null }
                    }
                },
                onDelete = (current as? ProfileEditor.Existing)?.profile?.takeUnless { it.isPrimary }?.let { profile ->
                    { onDelete(profile) { editor = null } }
                },
                onDismiss = {
                    onClearError()
                    editor = null
                }
            )
        }
    }
}

@Composable
private fun ProfileTile(
    label: String,
    selected: Boolean,
    editing: Boolean,
    avatar: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(168.dp),
        shape = ClickableSurfaceDefaults.shape(BrowseShapes.Panel),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = BrowseColors.Brand.copy(alpha = 0.16f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, BrowseColors.Brand.copy(alpha = 0.5f)), shape = BrowseShapes.Panel) else Border.None,
            focusedBorder = Border(BorderStroke(BrowseFocusSpec.BorderWidth, BrowseColors.Focus), shape = BrowseShapes.Panel)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                avatar()
                if (editing) {
                    Box(
                        modifier = Modifier.size(36.dp).background(BrowseColors.Surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = BrowseColors.TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = BrowseColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    editor: ProfileEditor,
    busy: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember(editor) {
        mutableStateOf((editor as? ProfileEditor.Existing)?.profile?.name.orEmpty())
    }
    PremiumDialog(
        title = stringResource(if (editor is ProfileEditor.New) R.string.profiles_new_title else R.string.profiles_edit_title),
        subtitle = if (onDelete != null) stringResource(R.string.profiles_delete_note) else null,
        onDismissRequest = { if (!busy) onDismiss() },
        heightFraction = null,
        content = {
            TvOutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text(stringResource(R.string.profiles_name), color = BrowseColors.TextSecondary) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank() && !busy) onSave(name) }),
                colors = { containerFocused -> profileTextFieldColors(containerFocused) }
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(profileErrorText(error), color = Color(0xFFF0A898), style = MaterialTheme.typography.bodyMedium)
            }
        },
        footer = {
            if (onDelete != null) {
                PremiumDialogFooterButton(
                    label = stringResource(R.string.profiles_delete),
                    destructive = true,
                    enabled = !busy,
                    onClick = onDelete
                )
                Spacer(Modifier.weight(1f))
            }
            PremiumDialogFooterButton(
                label = stringResource(R.string.profiles_cancel),
                enabled = !busy,
                onClick = onDismiss
            )
            PremiumDialogFooterButton(
                label = stringResource(R.string.profiles_save),
                emphasized = true,
                enabled = !busy && name.isNotBlank(),
                onClick = { onSave(name) }
            )
        }
    )
}

@Composable
private fun profileErrorText(error: String): String = when (error) {
    ViewerProfileErrors.NAME_TAKEN -> stringResource(R.string.profiles_error_name_taken)
    ViewerProfileErrors.NAME_EMPTY -> stringResource(R.string.profiles_error_name_empty)
    else -> stringResource(R.string.profiles_error_generic)
}

@Composable
private fun profileTextFieldColors(containerFocused: Boolean) = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = BrowseColors.TextPrimary,
    unfocusedTextColor = BrowseColors.TextPrimary,
    disabledTextColor = BrowseColors.TextDisabled,
    cursorColor = BrowseColors.Brand,
    focusedBorderColor = BrowseColors.Brand,
    unfocusedBorderColor = if (containerFocused) BrowseColors.Brand else BrowseColors.Outline,
    disabledBorderColor = BrowseColors.Outline.copy(alpha = 0.5f),
    focusedLabelColor = BrowseColors.Brand,
    unfocusedLabelColor = BrowseColors.TextSecondary,
    disabledLabelColor = BrowseColors.TextTertiary,
    focusedContainerColor = BrowseColors.Surface,
    unfocusedContainerColor = BrowseColors.Surface,
    disabledContainerColor = BrowseColors.Surface,
)

/** Small person icon used by the top bar button before any profile exists. */
@Composable
internal fun ProfilePlaceholderIcon(modifier: Modifier = Modifier) {
    Icon(Icons.Default.Person, contentDescription = null, modifier = modifier)
}
