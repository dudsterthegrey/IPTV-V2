package com.streamvault.data.search

import java.text.Normalizer
import java.util.Locale

/**
 * Typo-tolerant title matching, used when the exact (FTS) search finds little. A query word
 * matches a title word when it is a prefix of it (still typing) or within a few edits of it
 * (one for words up to 7 letters, two for longer ones; swapped letters count as one edit).
 * Every query word of 3+ letters must match; shorter ones only help the score. A query typed
 * without spaces ("spiderman") can also match a spaced title ("Spider-Man").
 */
object FuzzyTitleMatcher {

    /** Lowercase, accent-free words of [title], without a leading "US:"/"UK |" style tag. */
    fun tokenize(title: String): List<String> {
        if (title.isBlank()) return emptyList()
        var text = Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        text = LEADING_TAG.replace(text, "")
        text = text.replace("&", " and ").replace("+", " plus ")
        return text.split(NON_ALPHANUMERIC).filter { it.isNotEmpty() }
    }

    /**
     * Match cost of [titleTokens] for [queryTokens]; lower is better, null means no match.
     * [titleTokens] should come from [tokenize].
     */
    fun score(queryTokens: List<String>, titleTokens: List<String>): Int? {
        if (queryTokens.isEmpty() || titleTokens.isEmpty()) return null
        var cost = 0
        var matchedSignificant = 0
        var significant = 0
        for (query in queryTokens) {
            val isSignificant = query.length >= MIN_SIGNIFICANT_LENGTH
            if (isSignificant) significant++
            val best = titleTokens.minOfOrNull { tokenDistance(query, it) } ?: Int.MAX_VALUE
            when {
                best <= maxEdits(query.length) -> {
                    cost += best * EDIT_COST
                    if (isSignificant) matchedSignificant++
                }
                isSignificant -> {
                    // Fall back to matching the words joined up ("spider man" vs "spiderman").
                    return joinedScore(queryTokens, titleTokens)
                }
                else -> cost += MISSING_SHORT_WORD_COST
            }
        }
        if (significant > 0 && matchedSignificant < significant) return null
        // Prefer titles with fewer extra words ("Breaking Bad" over "Breaking Bad: Behind the Scenes").
        return cost + (titleTokens.size - queryTokens.size).coerceAtLeast(0)
    }

    private fun joinedScore(queryTokens: List<String>, titleTokens: List<String>): Int? {
        val joinedQuery = queryTokens.joinToString("")
        if (joinedQuery.length < MIN_JOINED_LENGTH) return null
        val joinedTitle = titleTokens.joinToString("")
        val allowed = maxEdits(joinedQuery.length)
        // Compare against the start of the title, allowing it to be a little longer or shorter.
        var best = Int.MAX_VALUE
        for (length in (joinedQuery.length - allowed)..(joinedQuery.length + allowed)) {
            if (length <= 0 || length > joinedTitle.length) continue
            best = minOf(best, boundedDistance(joinedQuery, joinedTitle.substring(0, length), allowed))
        }
        if (best > allowed) return null
        return best * EDIT_COST + JOINED_PENALTY + titleTokens.size
    }

    private fun tokenDistance(query: String, word: String): Int {
        if (word.startsWith(query)) return 0
        val allowed = maxEdits(query.length)
        if (allowed == 0) return if (query == word) 0 else Int.MAX_VALUE
        var best = boundedDistance(query, word, allowed)
        // A typo in a word that is still being typed: compare with the word's start too.
        if (word.length > query.length) {
            best = minOf(best, boundedDistance(query, word.substring(0, query.length), allowed))
        }
        return best
    }

    internal fun maxEdits(length: Int): Int = when {
        length < 4 -> 0
        length <= 7 -> 1
        else -> 2
    }

    /** Optimal string alignment distance, or a value above [max] once it is certain to exceed it. */
    internal fun boundedDistance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a == b) return 0
        val width = b.length + 1
        var previous2 = IntArray(width)
        var previous = IntArray(width) { it }
        var current = IntArray(width)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val substitution = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + substitution
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, previous2[j - 2] + 1)
                }
                current[j] = value
                if (value < rowMin) rowMin = value
            }
            if (rowMin > max) return max + 1
            val recycled = previous2
            previous2 = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }

    private const val MIN_SIGNIFICANT_LENGTH = 3
    private const val MIN_JOINED_LENGTH = 5
    private const val EDIT_COST = 10
    private const val JOINED_PENALTY = 5
    private const val MISSING_SHORT_WORD_COST = 3
    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val LEADING_TAG = Regex("^\\s*[a-z0-9]{2,4}\\s*[:|]\\s*")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
}
