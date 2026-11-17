package com.streamvault.app.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.CloudSyncRepository
import com.streamvault.domain.repository.ViewerProfileRepository
import com.streamvault.domain.repository.ViewerProfileState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Profiles of the active provider, for the "Who's watching?" screen and the top bar button. */
@HiltViewModel
class ViewerProfilesViewModel @Inject constructor(
    private val profiles: ViewerProfileRepository,
    private val cloudSync: CloudSyncRepository
) : ViewModel() {
    /** Null until first loaded. */
    val state: StateFlow<ViewerProfileState?> = profiles.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    /** An error code from [com.streamvault.domain.repository.ViewerProfileErrors], or another message. */
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    fun clearError() {
        mutableError.value = null
    }

    fun switchTo(profileId: Long, onDone: () -> Unit) = run(onDone) {
        val result = profiles.switchTo(profileId)
        if (result is Result.Success) refreshFromCloud()
        result
    }

    fun add(name: String, primaryName: String, onDone: () -> Unit) = run(onDone) {
        val providerId = state.value?.providerId ?: return@run Result.error(com.streamvault.domain.repository.ViewerProfileErrors.NO_PROVIDER)
        profiles.addProfile(providerId, name, primaryName).map { }
    }

    fun rename(profileId: Long, name: String, onDone: () -> Unit) = run(onDone) {
        profiles.renameProfile(profileId, name)
    }

    fun delete(profileId: Long, onDone: () -> Unit) = run(onDone) {
        val wasActive = state.value?.active?.id == profileId
        val result = profiles.deleteProfile(profileId)
        if (wasActive && result is Result.Success) refreshFromCloud()
        result
    }

    /** Fetch the newly active profile's progress from other devices, if signed in. */
    private fun refreshFromCloud() {
        viewModelScope.launch {
            if (cloudSync.state.first().signedInEmail != null) cloudSync.syncEverything()
        }
    }

    private fun run(onDone: () -> Unit, block: suspend () -> Result<Unit>) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        viewModelScope.launch {
            val result = try {
                block()
            } finally {
                mutableBusy.value = false
            }
            if (result is Result.Error) mutableError.value = result.message else onDone()
        }
    }
}
