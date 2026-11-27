package com.streamvault.feature.catalog.presentation.subtitles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.ExternalSubtitle
import com.streamvault.domain.model.ExternalSubtitleLanguage
import com.streamvault.domain.model.Result
import com.streamvault.domain.settings.PlayerPreferences
import com.streamvault.domain.settings.VodTrackPreference
import com.streamvault.domain.settings.VodTrackPreferenceScope
import com.streamvault.domain.settings.VodTrackPreferences
import com.streamvault.domain.usecase.DownloadExternalSubtitle
import com.streamvault.domain.usecase.GetExternalSubtitleLanguages
import com.streamvault.domain.usecase.SearchExternalSubtitles
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the subtitles picker on a movie or show page searches for and saves to. */
data class DetailSubtitleTarget(
    val providerId: Long,
    /** Movie id, or the id of the episode the Play button starts. */
    val contentId: Long,
    val contentType: ContentType,
    val title: String,
    val year: Int? = null,
    val tmdbId: Long? = null,
    val parentTmdbId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
) {
    internal val scope: VodTrackPreferenceScope?
        get() = when {
            providerId <= 0L || contentId <= 0L -> null
            contentType == ContentType.SERIES_EPISODE -> VodTrackPreferenceScope.Episode(providerId, contentId)
            contentType == ContentType.MOVIE || contentType == ContentType.VOD ->
                VodTrackPreferenceScope.Movie(providerId, contentId)
            else -> null
        }
}

fun com.streamvault.domain.model.Movie.toSubtitleTarget(): DetailSubtitleTarget = DetailSubtitleTarget(
    providerId = providerId,
    contentId = id,
    contentType = ContentType.MOVIE,
    title = name,
    year = year?.trim()?.take(4)?.toIntOrNull(),
    tmdbId = tmdbId?.takeIf { it > 0L },
)

fun com.streamvault.domain.model.Episode.toSubtitleTarget(
    series: com.streamvault.domain.model.Series,
): DetailSubtitleTarget = DetailSubtitleTarget(
    providerId = providerId.takeIf { it > 0L } ?: series.providerId,
    contentId = id,
    contentType = ContentType.SERIES_EPISODE,
    title = series.name,
    parentTmdbId = series.tmdbId?.takeIf { it > 0L },
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
)

enum class DetailSubtitleStage { CLOSED, LANGUAGES, RESULTS }

enum class DetailSubtitleProblem { LANGUAGES_FAILED, SEARCH_FAILED, DOWNLOAD_FAILED }

data class DetailSubtitleUiState(
    val stage: DetailSubtitleStage = DetailSubtitleStage.CLOSED,
    val languages: List<ExternalSubtitleLanguage> = emptyList(),
    val isLoading: Boolean = false,
    /** null means all languages. */
    val selectedLanguage: ExternalSubtitleLanguage? = null,
    val results: List<ExternalSubtitle> = emptyList(),
    val downloadingId: String? = null,
    val problem: DetailSubtitleProblem? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DetailSubtitlesViewModel @Inject constructor(
    private val getLanguages: GetExternalSubtitleLanguages,
    private val searchSubtitles: SearchExternalSubtitles,
    private val downloadSubtitle: DownloadExternalSubtitle,
    private val playerPreferences: PlayerPreferences,
) : ViewModel() {
    private val target = MutableStateFlow<DetailSubtitleTarget?>(null)
    private val mutableState = MutableStateFlow(DetailSubtitleUiState())
    val state: StateFlow<DetailSubtitleUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    /** The subtitle saved for the current target, shown on the page's button. */
    val chosenSubtitle: StateFlow<VodTrackPreference?> = target
        .flatMapLatest { current ->
            val scope = current?.scope ?: return@flatMapLatest flowOf(null)
            playerPreferences.getVodTrackPreferences(scope).map { prefs ->
                prefs?.subtitle?.takeIf { it.externalSubtitleDownloadId != null }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTarget(value: DetailSubtitleTarget?) {
        if (target.value == value) return
        target.value = value
        if (mutableState.value.stage != DetailSubtitleStage.CLOSED) close()
    }

    fun open() {
        if (target.value?.scope == null) return
        mutableState.update { it.copy(stage = DetailSubtitleStage.LANGUAGES, problem = null) }
        if (mutableState.value.languages.isNotEmpty()) return
        launchJob {
            mutableState.update { it.copy(isLoading = true) }
            when (val result = getLanguages()) {
                is Result.Success -> mutableState.update {
                    it.copy(isLoading = false, languages = orderLanguages(result.data))
                }
                else -> mutableState.update {
                    it.copy(isLoading = false, problem = DetailSubtitleProblem.LANGUAGES_FAILED)
                }
            }
        }
    }

    fun close() {
        job?.cancel()
        mutableState.update {
            it.copy(stage = DetailSubtitleStage.CLOSED, isLoading = false, downloadingId = null, problem = null)
        }
    }

    fun backToLanguages() {
        job?.cancel()
        mutableState.update {
            it.copy(stage = DetailSubtitleStage.LANGUAGES, isLoading = false, downloadingId = null, problem = null)
        }
    }

    fun selectLanguage(language: ExternalSubtitleLanguage?) {
        mutableState.update { it.copy(selectedLanguage = language) }
        search()
    }

    fun search() {
        val current = target.value ?: return
        mutableState.update {
            it.copy(stage = DetailSubtitleStage.RESULTS, results = emptyList(), problem = null)
        }
        launchJob {
            mutableState.update { it.copy(isLoading = true) }
            val result = searchSubtitles(
                contentType = if (current.contentType == ContentType.SERIES_EPISODE) {
                    ContentType.SERIES_EPISODE
                } else {
                    ContentType.MOVIE
                },
                title = current.title,
                year = current.year,
                tmdbId = current.tmdbId,
                parentTmdbId = current.parentTmdbId,
                seasonNumber = current.seasonNumber,
                episodeNumber = current.episodeNumber,
                language = mutableState.value.selectedLanguage?.code,
            )
            mutableState.update {
                when (result) {
                    is Result.Success -> it.copy(isLoading = false, results = result.data)
                    else -> it.copy(isLoading = false, problem = DetailSubtitleProblem.SEARCH_FAILED)
                }
            }
        }
    }

    fun choose(subtitle: ExternalSubtitle) {
        val scope = target.value?.scope ?: return
        mutableState.update { it.copy(downloadingId = subtitle.id, problem = null) }
        launchJob {
            when (downloadSubtitle(subtitle)) {
                is Result.Success -> {
                    val previous = playerPreferences.getVodTrackPreferences(scope).first()
                    playerPreferences.setVodTrackPreferences(
                        scope,
                        (previous ?: VodTrackPreferences()).copy(
                            subtitle = VodTrackPreference(
                                language = subtitle.language,
                                label = subtitle.displayLanguage,
                                externalSubtitleDownloadId = subtitle.downloadId,
                            )
                        )
                    )
                    previous?.subtitle?.externalSubtitleDownloadId
                        ?.takeIf { it != subtitle.downloadId }
                        ?.let { downloadSubtitle.deleteCachedSubtitle(it) }
                    mutableState.update { it.copy(downloadingId = null) }
                    close()
                }
                else -> mutableState.update {
                    it.copy(downloadingId = null, problem = DetailSubtitleProblem.DOWNLOAD_FAILED)
                }
            }
        }
    }

    fun removeChosen() {
        val scope = target.value?.scope ?: return
        viewModelScope.launch {
            try {
                val previous = playerPreferences.getVodTrackPreferences(scope).first() ?: return@launch
                val downloadId = previous.subtitle?.externalSubtitleDownloadId ?: return@launch
                playerPreferences.setVodTrackPreferences(scope, previous.copy(subtitle = null))
                downloadSubtitle.deleteCachedSubtitle(downloadId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keeping the old choice is harmless; the button still shows it.
            }
        }
        close()
    }

    /** Runs [block] as the single active job; failures become a problem state, never a crash. */
    private fun launchJob(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    val problem = when (it.stage) {
                        DetailSubtitleStage.RESULTS ->
                            if (it.downloadingId != null) DetailSubtitleProblem.DOWNLOAD_FAILED
                            else DetailSubtitleProblem.SEARCH_FAILED
                        else -> DetailSubtitleProblem.LANGUAGES_FAILED
                    }
                    it.copy(isLoading = false, downloadingId = null, problem = problem)
                }
            }
        }
    }

    /** The device language first, then English, then the rest alphabetically. */
    private fun orderLanguages(languages: List<ExternalSubtitleLanguage>): List<ExternalSubtitleLanguage> {
        val device = Locale.getDefault().language.lowercase(Locale.ROOT)
        val preferred = listOf(device, "en").distinct()
        val sorted = languages.sortedBy { it.name.lowercase(Locale.ROOT) }
        val first = preferred.mapNotNull { code -> sorted.firstOrNull { it.code.lowercase(Locale.ROOT) == code } }
        return first + sorted.filterNot { it in first }
    }
}
