package com.streamvault.feature.catalog.presentation.subtitles

import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.ExternalSubtitle
import com.streamvault.domain.model.ExternalSubtitleLanguage
import com.streamvault.domain.model.ExternalSubtitleSource
import com.streamvault.domain.model.Result
import com.streamvault.domain.repository.ExternalSubtitleRepository
import com.streamvault.domain.settings.PlayerPreferences
import com.streamvault.domain.settings.VodTrackPreference
import com.streamvault.domain.settings.VodTrackPreferenceScope
import com.streamvault.domain.settings.VodTrackPreferences
import com.streamvault.domain.usecase.DownloadExternalSubtitle
import com.streamvault.domain.usecase.GetExternalSubtitleLanguages
import com.streamvault.domain.usecase.SearchExternalSubtitles
import com.streamvault.feature.catalog.presentation.CatalogMainDispatcherRule
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class DetailSubtitlesViewModelTest {
    @get:Rule
    val mainDispatcherRule = CatalogMainDispatcherRule()

    private val subtitle = ExternalSubtitle(
        id = "s1", language = "en", displayLanguage = "English",
        releaseName = "New.Girl.S06E03", source = ExternalSubtitleSource.OPENSUBTITLES, downloadId = "4242"
    )

    private class FakeRepository(
        var searchResult: Result<List<ExternalSubtitle>>,
        var downloadResult: Result<File> = Result.success(File("4242.srt")),
    ) : ExternalSubtitleRepository {
        val searches = mutableListOf<Triple<ContentType, String, Int?>>()
        val deleted = mutableListOf<String>()
        override suspend fun search(
            contentType: ContentType, title: String, year: Int?, tmdbId: Long?, parentTmdbId: Long?,
            seasonNumber: Int?, episodeNumber: Int?, language: String?
        ): Result<List<ExternalSubtitle>> {
            searches += Triple(contentType, title, episodeNumber)
            return searchResult
        }
        override suspend fun languages() = Result.success(
            listOf(ExternalSubtitleLanguage("fr", "French"), ExternalSubtitleLanguage("en", "English"))
        )
        override suspend fun downloadToCache(subtitle: ExternalSubtitle) = downloadResult
        override suspend fun deleteCachedSubtitle(downloadId: String) { deleted += downloadId }
    }

    private val stored = MutableStateFlow<Map<VodTrackPreferenceScope, VodTrackPreferences>>(emptyMap())
    private val preferences: PlayerPreferences = mock {
        on { getVodTrackPreferences(any()) } doAnswer { invocation ->
            val scope = invocation.getArgument<VodTrackPreferenceScope>(0)
            stored.map { it[scope] }
        }
        onBlocking { setVodTrackPreferences(any(), any()) } doAnswer { invocation ->
            stored.value = stored.value + (invocation.getArgument<VodTrackPreferenceScope>(0) to
                invocation.getArgument<VodTrackPreferences>(1))
            Unit
        }
    }

    private fun viewModel(repository: FakeRepository) = DetailSubtitlesViewModel(
        GetExternalSubtitleLanguages(repository),
        SearchExternalSubtitles(repository),
        DownloadExternalSubtitle(repository),
        preferences,
    )

    private val episodeTarget = DetailSubtitleTarget(
        providerId = 1L, contentId = 77L, contentType = ContentType.SERIES_EPISODE,
        title = "New Girl", seasonNumber = 6, episodeNumber = 3
    )

    @Test
    fun `choosing an episode subtitle saves it for that episode only`() = runTest {
        val repository = FakeRepository(Result.success(listOf(subtitle)))
        val vm = viewModel(repository)
        vm.setTarget(episodeTarget)
        vm.open(); advanceUntilIdle()
        vm.selectLanguage(null); advanceUntilIdle()
        assertThat(vm.state.value.results).containsExactly(subtitle)
        assertThat(repository.searches.single()).isEqualTo(Triple(ContentType.SERIES_EPISODE, "New Girl", 3))

        vm.choose(subtitle); advanceUntilIdle()

        val saved = stored.value[VodTrackPreferenceScope.Episode(1L, 77L)]?.subtitle
        assertThat(saved).isEqualTo(VodTrackPreference(language = "en", label = "English", externalSubtitleDownloadId = "4242"))
        assertThat(stored.value.keys).containsExactly(VodTrackPreferenceScope.Episode(1L, 77L))
        assertThat(vm.state.value.stage).isEqualTo(DetailSubtitleStage.CLOSED)
    }

    @Test
    fun `movie choice keeps the saved audio preference and replaces the old file`() = runTest {
        val movieScope = VodTrackPreferenceScope.Movie(1L, 5L)
        val audio = VodTrackPreference(language = "de")
        stored.value = mapOf(movieScope to VodTrackPreferences(
            audio = audio,
            subtitle = VodTrackPreference(externalSubtitleDownloadId = "old")
        ))
        val repository = FakeRepository(Result.success(listOf(subtitle)))
        val vm = viewModel(repository)
        vm.setTarget(DetailSubtitleTarget(1L, 5L, ContentType.MOVIE, "Alien", year = 1979))

        vm.choose(subtitle); advanceUntilIdle()

        assertThat(stored.value[movieScope]?.audio).isEqualTo(audio)
        assertThat(stored.value[movieScope]?.subtitle?.externalSubtitleDownloadId).isEqualTo("4242")
        assertThat(repository.deleted).containsExactly("old")
    }

    @Test
    fun `failed search and download show a problem instead of crashing`() = runTest {
        val repository = FakeRepository(
            searchResult = Result.error("boom"),
            downloadResult = Result.error("nope")
        )
        val vm = viewModel(repository)
        vm.setTarget(episodeTarget)
        vm.selectLanguage(null); advanceUntilIdle()
        assertThat(vm.state.value.problem).isEqualTo(DetailSubtitleProblem.SEARCH_FAILED)

        vm.choose(subtitle); advanceUntilIdle()
        assertThat(vm.state.value.problem).isEqualTo(DetailSubtitleProblem.DOWNLOAD_FAILED)
        assertThat(stored.value).isEmpty()
    }
}
