package com.streamvault.data.metadata

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.remote.tmdb.TmdbTitleCleaner
import com.streamvault.domain.model.CatalogGenre
import org.junit.Test

class GenreClassifierTest {

    @Test
    fun `provider genres and categories map to the fixed list`() {
        assertThat(GenreClassifier.classify("Animation, Sci-Fi & Fantasy, Action & Adventure", "Series-Drama"))
            .containsAtLeast(CatalogGenre.ANIMATION, CatalogGenre.SCI_FI, CatalogGenre.FANTASY, CatalogGenre.ACTION, CatalogGenre.DRAMA)
        assertThat(GenreClassifier.classify(null, "Movie-Horror")).containsExactly(CatalogGenre.HORROR)
        assertThat(GenreClassifier.classify(null, "Movies-Christmas")).containsExactly(CatalogGenre.CHRISTMAS)
        assertThat(GenreClassifier.classify(null, "Server 1")).isEmpty()
    }

    @Test
    fun `japanese animation and anime categories are anime`() {
        assertThat(GenreClassifier.classify(null, null, tmdbGenreIds = listOf(16), originalLanguage = "ja"))
            .containsAtLeast(CatalogGenre.ANIME, CatalogGenre.ANIMATION)
        assertThat(GenreClassifier.classify(null, null, tmdbGenreIds = listOf(16), originalLanguage = "en"))
            .doesNotContain(CatalogGenre.ANIME)
        assertThat(GenreClassifier.classify(null, "Series-Anime")).contains(CatalogGenre.ANIME)
    }

    @Test
    fun `genres round trip through storage`() {
        val genres = setOf(CatalogGenre.ANIME, CatalogGenre.ACTION)
        val stored = CatalogGenre.encode(genres)
        assertThat(stored).isEqualTo("|ACTION|ANIME|")
        assertThat(CatalogGenre.decode(stored)).containsExactlyElementsIn(genres)
    }

    @Test
    fun `provider decorations are removed before searching TMDB`() {
        assertThat(TmdbTitleCleaner.clean("Kevin Hart: Cold As Balls (2018)"))
            .isEqualTo(TmdbTitleCleaner.Cleaned("Kevin Hart: Cold As Balls", 2018))
        assertThat(TmdbTitleCleaner.clean("US: The Bear 4K").title).isEqualTo("The Bear")
        assertThat(TmdbTitleCleaner.clean("1883 (2021)")).isEqualTo(TmdbTitleCleaner.Cleaned("1883", 2021))
    }
}
