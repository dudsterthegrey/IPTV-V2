package com.streamvault.domain.model

/**
 * A single stream the user saved outside any provider (IPTV Player's V1 "Direct URL" and
 * "Stream API" profiles). Picking one plays it immediately.
 */
data class SavedStream(
    val id: Long = 0L,
    val name: String,
    val kind: SavedStreamKind,
    /** The stream itself for [SavedStreamKind.DIRECT_URL]; the API endpoint for [SavedStreamKind.STREAM_API]. */
    val url: String,
    val createdAt: Long = 0L,
    val position: Int = 0
)

enum class SavedStreamKind {
    /** [SavedStream.url] is played as-is. */
    DIRECT_URL,

    /**
     * [SavedStream.url] is fetched on every play; the response (a plain-text URL, or JSON with a
     * `url`-like field) is the stream to play. For sources whose stream URL expires.
     */
    STREAM_API
}
