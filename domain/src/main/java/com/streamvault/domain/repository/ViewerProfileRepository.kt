package com.streamvault.domain.repository

import com.streamvault.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * A person watching under a provider. Each profile has its own watch progress, watched marks,
 * favorites, custom groups, tracked shows and search history for that provider.
 *
 * The [isPrimary] profile holds whatever existed before profiles were added.
 */
data class ViewerProfile(
    val id: Long,
    val providerId: Long,
    val name: String,
    val colorIndex: Int,
    val isPrimary: Boolean,
    val isActive: Boolean
)

/** The active provider's profiles; empty while it has only its original, unnamed profile. */
data class ViewerProfileState(
    val providerId: Long? = null,
    val profiles: List<ViewerProfile> = emptyList()
) {
    val active: ViewerProfile? get() = profiles.firstOrNull { it.isActive }
}

object ViewerProfileErrors {
    const val NAME_EMPTY = "NAME_EMPTY"
    const val NAME_TAKEN = "NAME_TAKEN"
    const val NO_PROVIDER = "NO_PROVIDER"
}

interface ViewerProfileRepository {
    /** Profiles of the active provider. */
    val state: Flow<ViewerProfileState>

    /**
     * Adds a profile to [providerId]. The first time, the existing data becomes a primary profile
     * called [primaryName]. Does not switch to the new profile.
     */
    suspend fun addProfile(providerId: Long, name: String, primaryName: String): Result<ViewerProfile>

    suspend fun renameProfile(profileId: Long, name: String): Result<Unit>

    /** Deletes a non-primary profile and everything kept for it, switching away first if needed. */
    suspend fun deleteProfile(profileId: Long): Result<Unit>

    /** Makes [profileId] the active profile of its provider, swapping in its data. */
    suspend fun switchTo(profileId: Long): Result<Unit>

    /**
     * Identifies the active profile of [providerId] across devices: "" for the primary profile (or
     * when there are no profiles), otherwise the profile's name in lowercase.
     */
    suspend fun activeProfileKey(providerId: Long): String
}
