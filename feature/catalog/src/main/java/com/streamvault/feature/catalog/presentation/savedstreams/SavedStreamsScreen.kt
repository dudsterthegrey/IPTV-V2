package com.streamvault.feature.catalog.presentation.savedstreams

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.core.navigation.AppDestination
import com.streamvault.core.navigation.PlayerNavigationRequest
import com.streamvault.core.ui.components.TvOutlinedTextField
import com.streamvault.core.ui.design.AppColors
import com.streamvault.core.ui.design.BrowseShapes
import com.streamvault.core.ui.interaction.TvClickableSurface
import com.streamvault.core.ui.interaction.TvIconButton
import com.streamvault.domain.model.SavedStream
import com.streamvault.domain.model.SavedStreamKind
import com.streamvault.feature.catalog.R
import com.streamvault.feature.catalog.api.CatalogNavigationChrome
import com.streamvault.feature.catalog.api.CatalogScaffoldContent
import com.streamvault.feature.catalog.presentation.components.DetailActionButton
import kotlinx.coroutines.launch

/**
 * Streams saved outside any provider: a direct stream URL, or a Stream API URL that returns a
 * fresh stream URL each time. Selecting one plays it straight away.
 */
@Composable
fun SavedStreamsScreen(
    scaffold: CatalogScaffoldContent,
    onOpenPlayer: (PlayerNavigationRequest) -> Unit,
    viewModel: SavedStreamsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val streams by viewModel.streams.collectAsStateWithLifecycle()
    val resolvingId by viewModel.resolvingId.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SavedStream?>(null) }
    var deleting by remember { mutableStateOf<SavedStream?>(null) }
    val playFailedMessage = stringResource(R.string.saved_streams_play_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SavedStreamsEvent.Play -> onOpenPlayer(event.request)
                SavedStreamsEvent.PlayFailed -> Toast.makeText(context, playFailedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    scaffold(
        AppDestination.SavedStreams,
        stringResource(R.string.saved_streams_title),
        stringResource(R.string.saved_streams_subtitle),
        CatalogNavigationChrome.TopBar,
        true,
        true,
        true
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "add") {
                DetailActionButton(
                    label = stringResource(R.string.saved_streams_add),
                    onClick = { editing = SavedStream(name = "", kind = SavedStreamKind.DIRECT_URL, url = "") },
                    icon = Icons.Filled.Add,
                    primary = true
                )
            }
            if (streams.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.saved_streams_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.widthIn(max = 720.dp).padding(top = 8.dp)
                    )
                }
            }
            items(streams, key = { it.id }) { stream ->
                SavedStreamRow(
                    stream = stream,
                    isResolving = resolvingId == stream.id,
                    onPlay = { viewModel.play(stream) },
                    onEdit = { editing = stream },
                    onDelete = { deleting = stream }
                )
            }
        }
    }

    editing?.let { draft ->
        SavedStreamEditorDialog(
            initial = draft,
            onDismiss = { editing = null },
            onSave = { updated -> viewModel.save(updated).also { saved -> if (saved) editing = null } }
        )
    }
    deleting?.let { stream ->
        DeleteSavedStreamDialog(
            name = stream.name,
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.delete(stream)
                deleting = null
            }
        )
    }
}

@Composable
private fun SavedStreamRow(
    stream: SavedStream,
    isResolving: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvClickableSurface(
            onClick = onPlay,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AppColors.SurfaceElevated,
                focusedContainerColor = AppColors.SurfaceEmphasis
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = RoundedCornerShape(14.dp))
            ),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(
                    text = stream.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isResolving -> stringResource(R.string.saved_streams_resolving)
                        stream.kind == SavedStreamKind.STREAM_API ->
                            stringResource(R.string.saved_streams_kind_api) + " · " + stream.url
                        else -> stringResource(R.string.saved_streams_kind_direct) + " · " + stream.url
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        RowIconButton(icon = Icons.Filled.Edit, label = stringResource(R.string.saved_streams_edit), onClick = onEdit)
        RowIconButton(icon = Icons.Filled.Delete, label = stringResource(R.string.saved_streams_delete), onClick = onDelete)
    }
}

@Composable
private fun RowIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TvIconButton(
        onClick = onClick,
        scale = ButtonDefaults.scale(focusedScale = 1f),
        shape = ButtonDefaults.shape(shape = CircleShape),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, AppColors.Outline), shape = CircleShape),
            focusedBorder = Border(BorderStroke(2.dp, AppColors.Focus), shape = CircleShape)
        ),
        colors = ButtonDefaults.colors(
            containerColor = AppColors.SurfaceEmphasis,
            focusedContainerColor = AppColors.Brand,
            contentColor = AppColors.TextSecondary,
            focusedContentColor = AppColors.OnPrimary
        )
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

@Composable
private fun SavedStreamEditorDialog(
    initial: SavedStream,
    onDismiss: () -> Unit,
    onSave: suspend (SavedStream) -> Boolean
) {
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var url by remember { mutableStateOf(initial.url) }
    var showError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // On a TV remote the keyboard's Next/Done key moves on to the next control.
    val moveDown = KeyboardActions(
        onNext = { focusManager.moveFocus(FocusDirection.Down) },
        onDone = { focusManager.moveFocus(FocusDirection.Down) }
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.9f),
            shape = BrowseShapes.Panel,
            colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(
                        if (initial.id > 0L) R.string.saved_streams_edit_title else R.string.saved_streams_add_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.TextPrimary
                )
                TvOutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = { Text(stringResource(R.string.saved_streams_name), color = AppColors.TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = moveDown,
                    modifier = Modifier.fillMaxWidth(),
                    colors = { focused -> savedStreamFieldColors(focused) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailActionButton(
                        label = stringResource(R.string.saved_streams_kind_direct),
                        onClick = { kind = SavedStreamKind.DIRECT_URL; showError = false },
                        primary = kind == SavedStreamKind.DIRECT_URL,
                        icon = null
                    )
                    DetailActionButton(
                        label = stringResource(R.string.saved_streams_kind_api),
                        onClick = { kind = SavedStreamKind.STREAM_API; showError = false },
                        primary = kind == SavedStreamKind.STREAM_API,
                        icon = null
                    )
                }
                TvOutlinedTextField(
                    value = url,
                    onValueChange = { url = it; showError = false },
                    label = {
                        Text(
                            stringResource(
                                if (kind == SavedStreamKind.STREAM_API) R.string.saved_streams_api_url else R.string.saved_streams_stream_url
                            ),
                            color = AppColors.TextSecondary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = moveDown,
                    modifier = Modifier.fillMaxWidth(),
                    colors = { focused -> savedStreamFieldColors(focused) }
                )
                Text(
                    text = stringResource(
                        if (kind == SavedStreamKind.STREAM_API) R.string.saved_streams_api_hint else R.string.saved_streams_direct_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.saved_streams_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Live
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailActionButton(label = stringResource(R.string.saved_streams_cancel), onClick = onDismiss)
                    DetailActionButton(
                        label = stringResource(R.string.saved_streams_save),
                        primary = true,
                        icon = null,
                        onClick = {
                            scope.launch {
                                val saved = onSave(initial.copy(name = name, kind = kind, url = url))
                                showError = !saved
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteSavedStreamDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = BrowseShapes.Panel,
            colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.saved_streams_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.saved_streams_delete_body, name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailActionButton(label = stringResource(R.string.saved_streams_cancel), onClick = onDismiss)
                    DetailActionButton(
                        label = stringResource(R.string.saved_streams_delete),
                        onClick = onConfirm,
                        icon = Icons.Filled.Delete
                    )
                }
            }
        }
    }
}

@Composable
private fun savedStreamFieldColors(containerFocused: Boolean) = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    cursorColor = AppColors.Brand,
    focusedBorderColor = AppColors.Brand,
    unfocusedBorderColor = if (containerFocused) AppColors.Brand else AppColors.Outline,
    focusedLabelColor = AppColors.Brand,
    unfocusedLabelColor = AppColors.TextSecondary,
    focusedContainerColor = AppColors.Surface,
    unfocusedContainerColor = AppColors.Surface,
)

