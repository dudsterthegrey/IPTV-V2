package com.streamvault.feature.catalog.presentation.dashboard

import com.streamvault.domain.model.AppHomeDashboardShelf

internal fun resolveVisibleDashboardShelves(
    uiState: DashboardUiState,
    keepRecentMovies: Boolean = false,
    keepRecentSeries: Boolean = false
): List<AppHomeDashboardShelf> = AppHomeDashboardShelf
    .normalizeForStorage(uiState.homeDashboardShelves)
    .filter { shelf ->
        // A filtered shelf stays, even when nothing matches, so its filters can be changed back.
        uiState.hasContentFor(shelf) ||
            (shelf == AppHomeDashboardShelf.RECENT_MOVIES && keepRecentMovies) ||
            (shelf == AppHomeDashboardShelf.RECENT_SERIES && keepRecentSeries)
    }

internal fun DashboardUiState.hasContentFor(shelf: AppHomeDashboardShelf): Boolean = when (shelf) {
    AppHomeDashboardShelf.FAVORITE_CHANNELS -> favoriteChannels.isNotEmpty()
    AppHomeDashboardShelf.RECENT_CHANNELS -> recentChannels.isNotEmpty()
    AppHomeDashboardShelf.LIVE_SHORTCUTS -> liveShortcuts.isNotEmpty()
    AppHomeDashboardShelf.CONTINUE_WATCHING -> continueWatching.isNotEmpty()
    AppHomeDashboardShelf.UP_NEXT -> upNext.isNotEmpty()
    AppHomeDashboardShelf.TOP_TEN -> topTen.isNotEmpty()
    AppHomeDashboardShelf.TRACKED_SERIES -> trackedSeries.isNotEmpty()
    AppHomeDashboardShelf.RECENT_EPISODES -> recentEpisodes.isNotEmpty()
    AppHomeDashboardShelf.PINNED_CATEGORIES -> pinnedMovieCategories.isNotEmpty() || pinnedSeriesCategories.isNotEmpty()
    AppHomeDashboardShelf.RECENT_MOVIES -> recentMovies.isNotEmpty()
    AppHomeDashboardShelf.RECENT_SERIES -> recentSeries.isNotEmpty()
    AppHomeDashboardShelf.FAVORITE_MOVIES -> favoriteMovies.isNotEmpty()
    AppHomeDashboardShelf.FAVORITE_SERIES -> favoriteSeries.isNotEmpty()
    AppHomeDashboardShelf.TRAKT_WATCHLIST_MOVIES -> traktWatchlistMovies.isNotEmpty()
    AppHomeDashboardShelf.TRAKT_WATCHLIST_SERIES -> traktWatchlistSeries.isNotEmpty()
    AppHomeDashboardShelf.CONTINUE_WATCHING_MOVIES -> continueWatchingMovies.isNotEmpty()
    AppHomeDashboardShelf.CONTINUE_WATCHING_SERIES -> continueWatchingSeriesItems.isNotEmpty()
    AppHomeDashboardShelf.TOP_RATED_MOVIES -> topRatedMovies.isNotEmpty()
    AppHomeDashboardShelf.RECOMMENDED_MOVIES -> recommendedMovies.isNotEmpty()
}
