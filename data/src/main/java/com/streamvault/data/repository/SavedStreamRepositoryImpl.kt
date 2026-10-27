package com.streamvault.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.streamvault.data.local.dao.SavedStreamDao
import com.streamvault.data.local.entity.SavedStreamEntity
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.SavedStream
import com.streamvault.domain.model.SavedStreamKind
import com.streamvault.domain.repository.SavedStreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedStreamRepositoryImpl @Inject constructor(
    private val dao: SavedStreamDao,
    private val okHttpClient: OkHttpClient
) : SavedStreamRepository {

    override fun observeSavedStreams(): Flow<List<SavedStream>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun save(stream: SavedStream): Result<Long> = try {
        val name = stream.name.trim()
        val url = stream.url.trim()
        when {
            name.isEmpty() -> Result.error("Name is required")
            !isAllowedSavedStreamUrl(url, stream.kind) -> Result.error("Unsupported URL")
            else -> {
                val isNew = stream.id <= 0L
                val id = dao.upsert(
                    SavedStreamEntity(
                        id = stream.id.coerceAtLeast(0L),
                        name = name,
                        kind = stream.kind.name,
                        url = url,
                        createdAt = if (isNew) System.currentTimeMillis() else stream.createdAt,
                        position = if (isNew) dao.nextPosition() else stream.position
                    )
                )
                Result.success(if (isNew) id else stream.id)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.error("Failed to save stream", e)
    }

    override suspend fun delete(id: Long): Result<Unit> = try {
        dao.delete(id)
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.error("Failed to delete stream", e)
    }

    override suspend fun resolvePlaybackUrl(stream: SavedStream): Result<String> = when (stream.kind) {
        SavedStreamKind.DIRECT_URL -> Result.success(stream.url.trim())
        SavedStreamKind.STREAM_API -> fetchStreamApiUrl(stream.url.trim())
    }

    private suspend fun fetchStreamApiUrl(apiUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(apiUrl).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.error("Stream API returned HTTP ${response.code}")
                }
                val source = response.body?.source()
                    ?: return@withContext Result.error("Stream API returned an empty response")
                val buffer = Buffer()
                // A stream URL is tiny; refuse to read whole files someone pointed this at.
                while (buffer.size < MAX_API_RESPONSE_BYTES && source.read(buffer, 8_192L) != -1L) Unit
                val streamUrl = parseStreamApiResponse(buffer.readUtf8())
                    ?: return@withContext Result.error("Stream API response did not contain a stream URL")
                Result.success(streamUrl)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.error("Stream API request failed", e)
        }
    }

    private companion object {
        const val MAX_API_RESPONSE_BYTES = 64 * 1024L
    }
}

private val playableSchemes = setOf("http", "https", "rtsp", "rtsps", "rtmp", "mms")
private val streamUrlJsonFields = listOf("url", "stream_url", "streamUrl", "stream", "link", "src")

/** Direct streams may use any playable scheme; a Stream API endpoint is always fetched over HTTP(S). */
internal fun isAllowedSavedStreamUrl(url: String, kind: SavedStreamKind): Boolean {
    val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase(Locale.ROOT)
    if (url.length <= scheme.length + 3) return false
    return when (kind) {
        SavedStreamKind.DIRECT_URL -> scheme in playableSchemes
        SavedStreamKind.STREAM_API -> scheme == "http" || scheme == "https"
    }
}

/**
 * Reads a Stream API response: JSON with a `url`-like top-level field (also accepted one level
 * down, e.g. `{"data": {"url": ...}}`), or plain text whose first line is the URL.
 */
internal fun parseStreamApiResponse(body: String): String? {
    val trimmed = body.trim().removePrefix("﻿")
    if (trimmed.isEmpty()) return null
    val candidate = if (trimmed.startsWith("{")) {
        runCatching { JsonParser.parseString(trimmed) }.getOrNull()?.let(::findStreamUrlField)
    } else {
        trimmed.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)?.trim('"')
    }
    return candidate?.trim()?.takeIf { isAllowedSavedStreamUrl(it, SavedStreamKind.DIRECT_URL) }
}

private fun findStreamUrlField(element: JsonElement, depth: Int = 0): String? {
    if (!element.isJsonObject || depth > 1) return null
    val obj = element.asJsonObject
    streamUrlJsonFields.forEach { field ->
        val value = obj.get(field)
        if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) return value.asString
    }
    return obj.entrySet().firstNotNullOfOrNull { (_, value) -> findStreamUrlField(value, depth + 1) }
}

private fun SavedStreamEntity.toDomain(): SavedStream? {
    val parsedKind = runCatching { SavedStreamKind.valueOf(kind) }.getOrNull() ?: return null
    return SavedStream(id = id, name = name, kind = parsedKind, url = url, createdAt = createdAt, position = position)
}
