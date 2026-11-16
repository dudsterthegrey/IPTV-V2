package com.streamvault.data.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FuzzyTitleMatcherTest {

    private fun score(query: String, title: String): Int? =
        FuzzyTitleMatcher.score(FuzzyTitleMatcher.tokenize(query), FuzzyTitleMatcher.tokenize(title))

    @Test
    fun `typos and swapped letters still match`() {
        assertThat(score("braking bad", "Breaking Bad (2008)")).isNotNull()
        assertThat(score("breakign bad", "Breaking Bad (2008)")).isNotNull()
        assertThat(score("intersteller", "Interstellar (2014)")).isNotNull()
        assertThat(score("naruto shipuden", "Naruto Shippūden (2007)")).isNotNull()
    }

    @Test
    fun `words typed together or partly typed match`() {
        assertThat(score("spiderman", "Spider-Man: No Way Home (2021)")).isNotNull()
        assertThat(score("game of thr", "Game of Thrones (2011)")).isNotNull()
    }

    @Test
    fun `country tags and accents are ignored`() {
        assertThat(score("pokemon", "US: Pokémon Journeys")).isNotNull()
    }

    @Test
    fun `unrelated titles and short words with typos do not match`() {
        assertThat(score("breaking bad", "Better Call Saul (2015)")).isNull()
        assertThat(score("cat", "Car Wars")).isNull()
        assertThat(score("intersteller", "Inception (2010)")).isNull()
    }

    @Test
    fun `closer matches score better`() {
        val exact = score("breaking bad", "Breaking Bad")!!
        val typo = score("braking bad", "Breaking Bad")!!
        val longer = score("breaking bad", "Breaking Bad: Behind the Scenes")!!
        assertThat(exact).isLessThan(typo)
        assertThat(exact).isLessThan(longer)
    }

    @Test
    fun `scoring a large catalog stays fast`() {
        val words = listOf("star", "night", "house", "dragon", "river", "love", "dark", "king", "city", "war")
        val titles = (0 until 80_000).map { i ->
            FuzzyTitleMatcher.tokenize("${words[i % 10]} ${words[(i / 10) % 10]} ${words[(i / 100) % 10]} $i")
        }
        val query = FuzzyTitleMatcher.tokenize("dragn kingg")
        val started = System.nanoTime()
        val matches = titles.count { FuzzyTitleMatcher.score(query, it) != null }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertThat(matches).isGreaterThan(0)
        assertThat(elapsedMs).isLessThan(1_500L)
    }
}
