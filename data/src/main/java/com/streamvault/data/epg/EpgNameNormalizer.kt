package com.streamvault.data.epg

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes channel display names for EPG matching.
 * Designed for comparing channel names across different EPG sources.
 *
 * This is intentionally simpler than [ChannelNormalizer.getLogicalGroupId]
 * — it does not strip quality tags or country prefixes because those may be
 * meaningful when matching EPG channels to provider channels.
 */
object EpgNameNormalizer {

    private val nonAlphanumericRegex = Regex("[^a-z0-9]")

    /**
     * Produces a normalized key from a channel display name.
     * - lowercase
     * - strip accents
     * - remove all non-alphanumeric characters
     *
     * Example: "BBC One HD" → "bbconehd"
     */
    fun normalize(name: String): String {
        if (name.isBlank()) return ""
        val lower = name.lowercase(Locale.ROOT)
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return stripped.replace(nonAlphanumericRegex, "")
    }

    private val countryPrefixRegex = Regex("^(usa|us|uk|gb|ca|canada)\\s*[:|\\-]?\\s+")
    private val pacificRegex = Regex("\\(?\\bpacific\\b\\)?")
    private val bracketRegex = Regex("[(\\[][^)\\]]*[)\\]]")
    private val droppedWordsRegex =
        Regex("\\b(hd|fhd|uhd|4k|sd|east|feed|channel|network|television|tv)\\b")

    /**
     * A looser key for a second matching pass, used only when [normalize] finds nothing:
     * drops a leading country tag ("USA News 12" → "news 12"), quality/feed words and bracketed
     * notes. "Pacific" counts as "west" so East and West feeds keep distinct keys.
     *
     * Example: "USA HBO Hits West*" and "HBO.Hits.(Pacific)" → "hbohitswest"
     */
    fun looseKey(name: String): String {
        if (name.isBlank()) return ""
        var s = Normalizer.normalize(name.lowercase(Locale.ROOT).replace('.', ' '), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        s = countryPrefixRegex.replace(s.trim(), "")
        s = pacificRegex.replace(s, " west ")
        s = bracketRegex.replace(s, " ")
        s = s.replace("&", " and ").replace("+", " plus ")
        s = droppedWordsRegex.replace(s, " ")
        return s.replace(nonAlphanumericRegex, "")
    }

    private val xmltvCountrySuffixRegex = Regex("\\.(us|uk|ca)\\d*$", RegexOption.IGNORE_CASE)

    /** Country from a leading tag on a provider channel name ("USA AMC" → "us"), or null. */
    fun countryOfChannelName(name: String): String? =
        countryPrefixRegex.find(name.lowercase(Locale.ROOT).trim())?.groupValues?.get(1)?.let(::canonicalCountry)

    /** Country from an XMLTV id suffix ("AMC.HD.us2" → "us"), or null. */
    fun countryOfXmltvId(xmltvId: String): String? =
        xmltvCountrySuffixRegex.find(xmltvId.trim())?.groupValues?.get(1)?.lowercase(Locale.ROOT)

    private fun canonicalCountry(tag: String): String = when (tag) {
        "usa", "us" -> "us"
        "uk", "gb" -> "uk"
        else -> "ca"
    }
}
