package com.streamvault.data.remote.subtitle

/**
 * Turns an IPTV provider title such as "EN - New Girl (2011) [MULTI-SUB]" or
 * "|US| New Girl S06E03 Homecoming" into the plain show or movie name that
 * OpenSubtitles can match ("New Girl").
 */
internal object SubtitleSearchTitle {
    private val providerPrefix = Regex(
        """^\s*(?:[\[(|]\s*[A-Za-z0-9+]{2,6}\s*[\])|]|[A-Z]{2}\s*:(?!\S)|[A-Z0-9+]{2,4}(?:-[A-Z0-9+]{2,4})?(?:\s+-|\s*\|)(?!\S))\s*"""
    )
    // Android's ICU regex engine rejects a bare closing }, unlike desktop Java; escape both.
    private val bracketed = Regex("""\[[^\]]*\]|\{[^}]*\}|\([^)]*\)""")
    private val episodeMarker = Regex(
        """(?i)(?:\b|_)(?:s\d{1,2}\s*[ex]\s*\d{1,3}|\d{1,2}x\d{2,3}|season\s*\d+|saison\s*\d+|staffel\s*\d+|s\d{1,2})\b.*$"""
    )
    private val qualityTag = Regex(
        """(?i)\s(?:4k|uhd|fhd|hd|sd|hevc|h\.?26[45]|x26[45]|1080p|720p|2160p|multi(?:-?subs?)?|vostfr|subbed|dubbed)$"""
    )
    private val separators = Regex("""[._]+""")
    private val releaseName = Regex("""[A-Za-z0-9]{2,}[._][A-Za-z0-9]{2,}""")
    private val whitespace = Regex("""\s+""")
    private val edgePunctuation = Regex("""^[\s\-:|]+|[\s\-:|]+$""")

    fun clean(raw: String): String {
        var title = raw.trim()
        repeat(2) { title = providerPrefix.replace(title, "") }
        title = bracketed.replace(title, " ")
        if (!title.contains(' ') && releaseName.containsMatchIn(title)) title = separators.replace(title, " ")
        title = episodeMarker.replace(title, "")
        title = whitespace.replace(title, " ").trim()
        while (true) {
            val stripped = edgePunctuation.replace(
                qualityTag.replace(title, ""),
                "",
            ).trim()
            if (stripped == title || stripped.isBlank()) break
            title = stripped
        }
        title = edgePunctuation.replace(title, "").trim()
        return title.ifBlank { raw.trim() }
    }
}
