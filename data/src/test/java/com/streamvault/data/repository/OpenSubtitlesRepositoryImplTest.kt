package com.streamvault.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.streamvault.data.remote.subtitle.OpenSubtitlesApi
import com.streamvault.data.remote.subtitle.OpenSubtitlesSessionStore
import com.streamvault.domain.model.ContentType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OpenSubtitlesRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var repository: OpenSubtitlesRepositoryImpl
    private lateinit var durableDirectory: File
    private lateinit var legacyDirectory: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = OpenSubtitlesRepositoryImpl(
            context = context,
            api = mock<OpenSubtitlesApi>(),
            sessionStore = mock<OpenSubtitlesSessionStore>(),
        )
        durableDirectory = File(context.filesDir, "subtitles/opensubtitles").apply { mkdirs() }
        legacyDirectory = File(context.cacheDir, "subtitles/opensubtitles").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        durableDirectory.deleteRecursively()
        legacyDirectory.deleteRecursively()
    }

    @Test
    fun `legacy cache file is moved to durable storage and removed`() = runBlocking {
        val legacy = File(legacyDirectory, "123.srt").apply { writeText("subtitle") }

        val cached = repository.cachedSubtitleFile("123")

        assertThat(cached).isNotNull()
        assertThat(cached!!.readText()).isEqualTo("subtitle")
        assertThat(cached.parentFile).isEqualTo(durableDirectory)
        assertThat(legacy.exists()).isFalse()
    }

    @Test
    fun `delete removes both durable and legacy cached files`() = runBlocking {
        val durable = File(durableDirectory, "456.srt").apply { writeText("subtitle") }
        val legacy = File(legacyDirectory, "456.srt").apply { writeText("subtitle") }

        repository.deleteCachedSubtitle("456")

        assertThat(durable.exists()).isFalse()
        assertThat(legacy.exists()).isFalse()
    }

    @Test
    fun `episode search tries tmdb, then title with season filters, then plain text`() {
        val attempts = repository.buildSearchAttempts(
            contentType = ContentType.SERIES_EPISODE,
            title = "EN - New Girl (2011)",
            year = null,
            tmdbId = null,
            parentTmdbId = 1420L,
            seasonNumber = 6,
            episodeNumber = 3,
            language = "en",
        )

        assertThat(attempts.map { it.toQueryParams() }).containsExactly(
            mapOf("parent_tmdb_id" to "1420", "season_number" to "6", "episode_number" to "3",
                "languages" to "en", "order_by" to "download_count"),
            mapOf("query" to "new girl", "season_number" to "6", "episode_number" to "3",
                "languages" to "en", "order_by" to "download_count"),
            mapOf("query" to "new girl s06e03", "languages" to "en", "order_by" to "download_count"),
        ).inOrder()
    }

    @Test
    fun `episode search without a tmdb id starts with the cleaned title`() {
        val attempts = repository.buildSearchAttempts(
            contentType = ContentType.SERIES_EPISODE,
            title = "New Girl",
            year = null,
            tmdbId = null,
            parentTmdbId = null,
            seasonNumber = 6,
            episodeNumber = 3,
            language = "auto",
        )

        assertThat(attempts.map { it.toQueryParams()["query"] })
            .containsExactly("new girl", "new girl s06e03").inOrder()
        assertThat(attempts.first().toQueryParams()).doesNotContainKey("languages")
    }

    @Test
    fun `movie search tries tmdb, then title with year, then title alone`() {
        val attempts = repository.buildSearchAttempts(
            contentType = ContentType.MOVIE,
            title = "EN - Alien",
            year = 1979,
            tmdbId = 348L,
            parentTmdbId = null,
            seasonNumber = null,
            episodeNumber = null,
            language = null,
        )

        assertThat(attempts.map { it.toQueryParams() - "order_by" }).containsExactly(
            mapOf("tmdb_id" to "348"),
            mapOf("query" to "alien", "year" to "1979"),
            mapOf("query" to "alien"),
        ).inOrder()
    }
}
