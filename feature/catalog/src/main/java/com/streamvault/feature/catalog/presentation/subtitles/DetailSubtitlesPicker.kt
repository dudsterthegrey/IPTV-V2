package com.streamvault.feature.catalog.presentation.subtitles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.streamvault.core.ui.components.dialogs.PremiumDialog
import com.streamvault.core.ui.components.dialogs.PremiumDialogActionButton
import com.streamvault.core.ui.components.dialogs.PremiumDialogFooterButton
import com.streamvault.core.ui.design.BrowseColors
import com.streamvault.feature.catalog.R
import com.streamvault.feature.catalog.presentation.components.DetailActionButton

/**
 * A "Subtitles" button for the movie or show page. The chosen OpenSubtitles file is downloaded
 * right away and saved for [target], and the player loads it when Play is pressed.
 */
@Composable
fun DetailSubtitlesButton(
    target: DetailSubtitleTarget?,
    viewModel: DetailSubtitlesViewModel = hiltViewModel(),
) {
    LaunchedEffect(target) { viewModel.setTarget(target) }
    if (target == null) return
    val chosen by viewModel.chosenSubtitle.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chosenLabel = chosen?.label?.takeIf { it.isNotBlank() } ?: chosen?.language
    DetailActionButton(
        label = if (chosenLabel != null) {
            stringResource(R.string.detail_subtitles_button_chosen, chosenLabel)
        } else {
            stringResource(R.string.detail_subtitles_button)
        },
        onClick = viewModel::open,
        icon = Icons.Filled.Subtitles,
    )
    if (state.stage != DetailSubtitleStage.CLOSED) {
        DetailSubtitlesDialog(
            target = target,
            state = state,
            hasChosen = chosen != null,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun DetailSubtitlesDialog(
    target: DetailSubtitleTarget,
    state: DetailSubtitleUiState,
    hasChosen: Boolean,
    viewModel: DetailSubtitlesViewModel,
) {
    val firstFocus = remember { FocusRequester() }
    val busy = state.isLoading || state.downloadingId != null
    val title = if (target.seasonNumber != null && target.episodeNumber != null) {
        stringResource(R.string.detail_subtitles_title_episode, target.seasonNumber, target.episodeNumber)
    } else {
        stringResource(R.string.detail_subtitles_title)
    }
    LaunchedEffect(state.stage, state.isLoading, state.languages.size, state.results.size) {
        if (!state.isLoading) runCatching { firstFocus.requestFocus() }
    }
    PremiumDialog(
        title = title,
        subtitle = stringResource(R.string.detail_subtitles_description),
        onDismissRequest = viewModel::close,
        widthFraction = 0.5f,
        content = {
            when {
                state.isLoading -> Text(
                    text = stringResource(
                        if (state.stage == DetailSubtitleStage.RESULTS) R.string.detail_subtitles_searching
                        else R.string.detail_subtitles_loading_languages
                    ),
                    color = BrowseColors.TextSecondary,
                )
                state.problem != null -> {
                    Text(text = stringResource(state.problem.messageRes()), color = BrowseColors.TextSecondary)
                    PremiumDialogActionButton(
                        label = stringResource(R.string.detail_subtitles_retry),
                        onClick = {
                            if (state.stage == DetailSubtitleStage.RESULTS) viewModel.search() else viewModel.open()
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                    )
                    if (state.problem == DetailSubtitleProblem.DOWNLOAD_FAILED) {
                        ResultRows(state, busy, viewModel, focus = null)
                    }
                }
                state.stage == DetailSubtitleStage.LANGUAGES -> {
                    PremiumDialogActionButton(
                        label = stringResource(R.string.detail_subtitles_all_languages),
                        onClick = { viewModel.selectLanguage(null) },
                        modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
                    )
                    state.languages.forEach { language ->
                        PremiumDialogActionButton(
                            label = language.name,
                            onClick = { viewModel.selectLanguage(language) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> {
                    if (state.results.isEmpty()) {
                        Text(
                            text = stringResource(R.string.detail_subtitles_no_results),
                            color = BrowseColors.TextSecondary,
                        )
                    }
                    ResultRows(state, busy, viewModel, focus = firstFocus)
                }
            }
        },
        footer = {
            if (hasChosen && state.stage == DetailSubtitleStage.LANGUAGES) {
                PremiumDialogFooterButton(
                    label = stringResource(R.string.detail_subtitles_remove),
                    onClick = viewModel::removeChosen,
                    enabled = !busy,
                )
            }
            if (state.stage == DetailSubtitleStage.RESULTS) {
                PremiumDialogFooterButton(
                    label = stringResource(R.string.detail_subtitles_change_language),
                    onClick = viewModel::backToLanguages,
                )
            }
            PremiumDialogFooterButton(
                label = stringResource(R.string.detail_subtitles_close),
                onClick = viewModel::close,
            )
        },
    )
}

@Composable
private fun ResultRows(
    state: DetailSubtitleUiState,
    busy: Boolean,
    viewModel: DetailSubtitlesViewModel,
    focus: FocusRequester?,
) {
    state.results.forEachIndexed { index, subtitle ->
        val release = subtitle.releaseName.ifBlank { stringResource(R.string.detail_subtitles_release_unknown) }
        val label = if (state.downloadingId == subtitle.id) {
            stringResource(R.string.detail_subtitles_downloading, subtitle.displayLanguage)
        } else {
            "${subtitle.displayLanguage} · $release"
        }
        PremiumDialogActionButton(
            label = label,
            // Rows stay enabled while busy: disabling the focused row would move TV focus away.
            onClick = { if (!busy) viewModel.choose(subtitle) },
            modifier = Modifier.fillMaxWidth().then(
                if (index == 0 && focus != null) Modifier.focusRequester(focus) else Modifier
            ),
        )
    }
}

private fun DetailSubtitleProblem.messageRes(): Int = when (this) {
    DetailSubtitleProblem.LANGUAGES_FAILED -> R.string.detail_subtitles_error_languages
    DetailSubtitleProblem.SEARCH_FAILED -> R.string.detail_subtitles_error_search
    DetailSubtitleProblem.DOWNLOAD_FAILED -> R.string.detail_subtitles_error_download
}
