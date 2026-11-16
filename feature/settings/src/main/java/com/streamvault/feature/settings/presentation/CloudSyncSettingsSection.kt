package com.streamvault.feature.settings.presentation

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.streamvault.core.ui.components.TvOutlinedTextField
import com.streamvault.core.ui.design.AppColors
import com.streamvault.core.ui.interaction.TvButton
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.CloudSyncRepository
import com.streamvault.domain.repository.CloudSyncState
import com.streamvault.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val cloudSync: CloudSyncRepository
) : ViewModel() {
    val state: StateFlow<CloudSyncState> = cloudSync.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudSyncState())

    suspend fun create(): Result<String> = cloudSync.createSyncCode()
    suspend fun join(code: String): Result<Unit> = cloudSync.joinSyncCode(code)
    suspend fun syncNow(): Result<Unit> = cloudSync.syncNow()
    fun leave() {
        viewModelScope.launch { cloudSync.leave() }
    }
}

/**
 * "Sync across devices": share watch progress between TVs with a sync code, so something started
 * in one room can be finished in another.
 */
@Composable
fun CloudSyncSettingsSection(viewModel: CloudSyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showJoinDialog by remember { mutableStateOf(false) }
    val failedMessage = stringResource(R.string.settings_cloud_sync_failed)
    val doneMessage = stringResource(R.string.settings_cloud_sync_done)

    fun report(result: Result<*>) {
        val message = when (result) {
            is Result.Error -> result.message.ifBlank { failedMessage }
            else -> doneMessage
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        val status = when {
            !state.isConfigured -> stringResource(R.string.settings_cloud_sync_not_configured)
            state.syncCode == null -> stringResource(R.string.settings_cloud_sync_off)
            state.lastSyncedAt > 0L -> stringResource(
                R.string.settings_cloud_sync_on_since,
                DateUtils.getRelativeTimeSpanString(state.lastSyncedAt).toString()
            )
            else -> stringResource(R.string.settings_cloud_sync_on)
        }
        SettingsRow(label = stringResource(R.string.settings_cloud_sync_title), value = status)
        if (!state.isConfigured) return@Column

        val code = state.syncCode
        if (code == null) {
            ClickableSettingsRow(
                label = stringResource(R.string.settings_cloud_sync_create),
                value = stringResource(R.string.settings_cloud_sync_create_value),
                onClick = { scope.launch { report(viewModel.create()) } }
            )
            ClickableSettingsRow(
                label = stringResource(R.string.settings_cloud_sync_join),
                value = stringResource(R.string.settings_cloud_sync_join_value),
                onClick = { showJoinDialog = true }
            )
        } else {
            SettingsRow(label = stringResource(R.string.settings_cloud_sync_code), value = code)
            ClickableSettingsRow(
                label = stringResource(R.string.settings_cloud_sync_now),
                value = stringResource(
                    if (state.isSyncing) R.string.settings_cloud_sync_syncing else R.string.settings_cloud_sync_now_value
                ),
                onClick = { if (!state.isSyncing) scope.launch { report(viewModel.syncNow()) } }
            )
            ClickableSettingsRow(
                label = stringResource(R.string.settings_cloud_sync_leave),
                value = stringResource(R.string.settings_cloud_sync_leave_value),
                onClick = viewModel::leave
            )
        }
        state.lastError?.let { error ->
            SettingsRow(label = stringResource(R.string.settings_cloud_sync_last_error), value = error)
        }
    }

    if (showJoinDialog) {
        var entered by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showJoinDialog = false }) {
            Surface {
                Column(modifier = Modifier.padding(24.dp).width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_cloud_sync_join), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_cloud_sync_join_hint), style = MaterialTheme.typography.bodyMedium)
                    TvOutlinedTextField(
                        value = entered,
                        onValueChange = { entered = it.take(20) },
                        label = { Text("K7QF-9M2P-XW4D") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = { focused ->
                            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary,
                                cursorColor = AppColors.Brand,
                                focusedBorderColor = AppColors.Brand,
                                unfocusedBorderColor = if (focused) AppColors.Brand else AppColors.Outline,
                                focusedLabelColor = AppColors.Brand,
                                unfocusedLabelColor = AppColors.TextSecondary
                            )
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(onClick = {
                            showJoinDialog = false
                            scope.launch { report(viewModel.join(entered)) }
                        }) { Text(stringResource(R.string.settings_cloud_sync_join_confirm)) }
                        TvButton(onClick = { showJoinDialog = false }) {
                            Text(stringResource(R.string.settings_cloud_sync_cancel))
                        }
                    }
                }
            }
        }
    }
}
