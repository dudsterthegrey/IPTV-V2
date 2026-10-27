package com.streamvault.domain.repository

import com.streamvault.domain.model.Result
import com.streamvault.domain.model.SavedStream
import kotlinx.coroutines.flow.Flow

interface SavedStreamRepository {
    fun observeSavedStreams(): Flow<List<SavedStream>>

    /** Inserts a new stream (id 0) or updates an existing one; returns its id. */
    suspend fun save(stream: SavedStream): Result<Long>
    suspend fun delete(id: Long): Result<Unit>

    /** The URL to hand the player: the saved URL itself, or the one a Stream API returns now. */
    suspend fun resolvePlaybackUrl(stream: SavedStream): Result<String>
}
