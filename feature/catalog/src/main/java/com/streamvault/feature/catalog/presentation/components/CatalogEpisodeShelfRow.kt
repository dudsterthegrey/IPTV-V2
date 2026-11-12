package com.streamvault.feature.catalog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.streamvault.core.ui.design.browse.GradientOverlayBottom
import com.streamvault.core.ui.design.browse.SurfaceElevated
import com.streamvault.core.ui.design.browse.TextPrimary
import com.streamvault.core.ui.design.browse.TextSecondary
import com.streamvault.core.ui.design.browse.TextTertiary
import com.streamvault.core.ui.image.rememberCrossfadeImageModel
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.EpisodeShelfItem
import com.streamvault.domain.model.PlaybackHistory
import com.streamvault.feature.catalog.R
import java.text.DateFormat
import java.util.Date

/**
 * A Home row of episode cards (Up Next, Recently added episodes): the show's name, then
 * "S1 E4" and, when [showAddedDate], the date the episode was added.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpisodeShelfRow(
    title: String,
    items: List<EpisodeShelfItem>,
    showAddedDate: Boolean,
    onItemClick: (EpisodeShelfItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().suppressParentVerticalScroll()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
        )

        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = items,
                key = { it.episode.id }
            ) { item ->
                EpisodeShelfTile(item = item, showAddedDate = showAddedDate, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun EpisodeShelfTile(
    item: EpisodeShelfItem,
    showAddedDate: Boolean,
    onClick: () -> Unit
) {
    val episode = item.episode
    val imageUrl = episode.coverUrl?.takeIf { it.isNotBlank() } ?: item.seriesPosterUrl
    val seasonEpisode = stringResource(
        R.string.continue_watching_season_episode,
        episode.seasonNumber,
        episode.episodeNumber
    )
    val detail = if (showAddedDate && episode.addedAt > 0L) {
        stringResource(R.string.dashboard_episode_added_on, seasonEpisode, formatAddedDate(episode.addedAt))
    } else {
        seasonEpisode
    }

    FocusableCard(
        onClick = onClick,
        width = 208.dp,
        height = 117.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            // Fallback always visible; covered by AsyncImage on successful load
            Text(
                text = stringResource(R.string.nav_series),
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary
            )
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = rememberCrossfadeImageModel(imageUrl),
                    contentDescription = item.seriesName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, GradientOverlayBottom)))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.seriesName,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatAddedDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

/** Plays the episode through the same path as a history entry; with no saved position it starts at 0. */
fun EpisodeShelfItem.toPlaybackHistory(): PlaybackHistory = PlaybackHistory(
    contentId = episode.id,
    contentType = ContentType.SERIES_EPISODE,
    providerId = episode.providerId,
    title = episode.title,
    posterUrl = episode.coverUrl?.takeIf { it.isNotBlank() } ?: seriesPosterUrl,
    streamUrl = episode.streamUrl,
    seriesId = episode.seriesId,
    seasonNumber = episode.seasonNumber,
    episodeNumber = episode.episodeNumber
)
