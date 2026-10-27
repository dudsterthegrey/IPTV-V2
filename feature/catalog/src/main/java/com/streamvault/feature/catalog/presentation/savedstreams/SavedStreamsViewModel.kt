package com.streamvault.feature.catalog.presentation.savedstreams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.core.navigation.AppDestination
import com.streamvault.core.navigation.PlayerNavigationRequest
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.SavedStream
import com.streamvault.domain.repository.SavedStreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedStreamsViewModel @Inject constructor(
    private val repository: SavedStreamRepository
) : ViewModel() {

    val streams: StateFlow<List<SavedStream>> = repository.observeSavedStreams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Id of the stream whose Stream API is being called right now, so its row can say so. */
    private val _resolvingId = MutableStateFlow<Long?>(null)
    val resolvingId: StateFlow<Long?> = _resolvingId.asStateFlow()

    private val _events = MutableSharedFlow<SavedStreamsEvent>()
    val events: SharedFlow<SavedStreamsEvent> = _events.asSharedFlow()

    fun play(stream: SavedStream) {
        if (_resolvingId.value != null) return
        _resolvingId.value = stream.id
        viewModelScope.launch {
            val resolved = repository.resolvePlaybackUrl(stream)
            _resolvingId.value = null
            when (resolved) {
                is Result.Success -> _events.emit(
                    SavedStreamsEvent.Play(
                        PlayerNavigationRequest(
                            streamUrl = resolved.data,
                            title = stream.name,
                            contentType = "LIVE",
                            returnDestination = AppDestination.SavedStreams
                        )
                    )
                )
                else -> _events.emit(SavedStreamsEvent.PlayFailed)
            }
        }
    }

    /** Returns true when saved; the editor stays open on failure. */
    suspend fun save(stream: SavedStream): Boolean =
        repository.save(stream) is Result.Success

    fun delete(stream: SavedStream) {
        viewModelScope.launch { repository.delete(stream.id) }
    }
}

sealed interface SavedStreamsEvent {
    data class Play(val request: PlayerNavigationRequest) : SavedStreamsEvent
    data object PlayFailed : SavedStreamsEvent
}
