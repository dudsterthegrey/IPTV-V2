package com.streamvault.feature.settings.presentation

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.streamvault.domain.repository.CloudSyncErrors
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

    suspend fun signIn(email: String, password: String): Result<Unit> = cloudSync.signIn(email, password)
    suspend fun createAccount(email: String, password: String): Result<Unit> = cloudSync.createAccount(email, password)
    suspend fun sendPasswordReset(email: String): Result<Unit> = cloudSync.sendPasswordReset(email)
    suspend fun syncNow(): Result<Unit> = cloudSync.syncNow()
    fun signOut() {
        viewModelScope.launch { cloudSync.signOut() }
    }
}

/**
 * "Sync across devices": sign in with the same account on each TV and watch progress follows
 * you, so something started in one room can be finished in another.
 */
@Composable
fun CloudSyncSettingsSection(viewModel: CloudSyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSignIn by remember { mutableStateOf(false) }
    val messages = CloudSyncMessages(
        done = stringResource(R.string.settings_cloud_sync_done),
        signedIn = stringResource(R.string.settings_cloud_sync_signed_in_toast),
        resetSent = stringResource(R.string.settings_cloud_sync_reset_sent),
        wrongCredentials = stringResource(R.string.settings_cloud_sync_error_credentials),
        emailInUse = stringResource(R.string.settings_cloud_sync_error_email_in_use),
        weakPassword = stringResource(R.string.settings_cloud_sync_error_weak_password),
        invalidEmail = stringResource(R.string.settings_cloud_sync_error_invalid_email),
        tooManyAttempts = stringResource(R.string.settings_cloud_sync_error_too_many),
        network = stringResource(R.string.settings_cloud_sync_failed)
    )

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        val status = when {
            !state.isConfigured -> stringResource(R.string.settings_cloud_sync_not_configured)
            state.signedInEmail == null -> stringResource(R.string.settings_cloud_sync_signed_out)
            state.lastSyncedAt > 0L -> stringResource(
                R.string.settings_cloud_sync_on_since,
                DateUtils.getRelativeTimeSpanString(state.lastSyncedAt).toString()
            )
            else -> stringResource(R.string.settings_cloud_sync_on)
        }
        SettingsRow(label = stringResource(R.string.settings_cloud_sync_title), value = status)
        if (!state.isConfigured) return@Column

        val email = state.signedInEmail
        if (email == null) {
            ClickableSettingsRow(
                label = stringResource(R.string.settings_cloud_sync_sign_in),
                value = stringResource(R.string.settings_cloud_sync_sign_in_value),
                onClick = { showSignIn = true }
            )
        } else {
            SettingsRow(label = stringResource(R.string.settings_cloud_sync_account), value = email)
            ClickableSettingsRow(
                label = stringResource(R.string.settings_cloud_sync_now),
                value = stringResource(
                    if (state.isSyncing) R.string.settings_cloud_sync_syncing else R.string.settings_cloud_sync_now_value
                ),
                onClick = {
                    if (!state.isSyncing) scope.launch { toast(messages.forResult(viewModel.syncNow(), messages.done)) }
                }
            )
            ClickableSettingsRow(
                label = stringResource(R.string.settings_cloud_sync_sign_out),
                value = stringResource(R.string.settings_cloud_sync_sign_out_value),
                onClick = viewModel::signOut
            )
        }
    }

    if (showSignIn) {
        CloudSignInDialog(
            onDismiss = { showSignIn = false },
            onSignIn = { emailInput, password ->
                scope.launch {
                    val result = viewModel.signIn(emailInput, password)
                    toast(messages.forResult(result, messages.signedIn))
                    if (result !is Result.Error) showSignIn = false
                }
            },
            onCreateAccount = { emailInput, password ->
                scope.launch {
                    val result = viewModel.createAccount(emailInput, password)
                    toast(messages.forResult(result, messages.signedIn))
                    if (result !is Result.Error) showSignIn = false
                }
            },
            onForgotPassword = { emailInput ->
                scope.launch { toast(messages.forResult(viewModel.sendPasswordReset(emailInput), messages.resetSent)) }
            }
        )
    }
}

private class CloudSyncMessages(
    val done: String,
    val signedIn: String,
    val resetSent: String,
    val wrongCredentials: String,
    val emailInUse: String,
    val weakPassword: String,
    val invalidEmail: String,
    val tooManyAttempts: String,
    val network: String
) {
    fun forResult(result: Result<*>, success: String): String = when (result) {
        is Result.Error -> when (result.message) {
            CloudSyncErrors.WRONG_EMAIL_OR_PASSWORD -> wrongCredentials
            CloudSyncErrors.EMAIL_IN_USE -> emailInUse
            CloudSyncErrors.WEAK_PASSWORD -> weakPassword
            CloudSyncErrors.INVALID_EMAIL -> invalidEmail
            CloudSyncErrors.TOO_MANY_ATTEMPTS -> tooManyAttempts
            else -> network
        }
        else -> success
    }
}

@Composable
private fun CloudSignInDialog(
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val ready = email.contains('@') && password.length >= 6

    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(modifier = Modifier.padding(24.dp).width(460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_cloud_sync_sign_in), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_cloud_sync_sign_in_hint), style = MaterialTheme.typography.bodyMedium)
                TvOutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text(stringResource(R.string.settings_cloud_sync_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = { focused -> signInFieldColors(focused) }
                )
                TvOutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_cloud_sync_password)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = { focused -> signInFieldColors(focused) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(onClick = { if (ready) onSignIn(email, password) }, enabled = ready) {
                        Text(stringResource(R.string.settings_cloud_sync_sign_in_action))
                    }
                    TvButton(onClick = { if (ready) onCreateAccount(email, password) }, enabled = ready) {
                        Text(stringResource(R.string.settings_cloud_sync_create_account))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton(onClick = { if (email.contains('@')) onForgotPassword(email) }, enabled = email.contains('@')) {
                        Text(stringResource(R.string.settings_cloud_sync_forgot_password))
                    }
                    TvButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_cloud_sync_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun signInFieldColors(containerFocused: Boolean) = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    cursorColor = AppColors.Brand,
    focusedBorderColor = AppColors.Brand,
    unfocusedBorderColor = if (containerFocused) AppColors.Brand else AppColors.Outline,
    focusedLabelColor = AppColors.Brand,
    unfocusedLabelColor = AppColors.TextSecondary
)
