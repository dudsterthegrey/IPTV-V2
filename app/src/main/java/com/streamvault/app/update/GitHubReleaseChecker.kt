package com.streamvault.app.update

import com.streamvault.app.BuildConfig
import com.streamvault.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IPTV Player publishes its APK in the repo (not as a GitHub Release), with a small manifest next
 * to it: {"versionName","versionCode","downloadUrl","sha256","releaseNotes"}.
 */
internal const val UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/dudsterthegrey/IPTV-V2/main/release/update.json"
internal const val UPDATE_RELEASE_PAGE_URL = "https://github.com/dudsterthegrey/IPTV-V2/tree/main/release"

data class GitHubReleaseInfo(
    val versionName: String,
    val versionCode: Int?,
    val releaseUrl: String,
    val downloadUrl: String?,
    val downloadSha256: String?,
    val releaseNotes: String,
    val publishedAt: String?
)

@Singleton
class GitHubReleaseChecker internal constructor(
    private val okHttpClient: OkHttpClient,
    private val updatesEnabled: Boolean,
) {
    @Inject constructor(okHttpClient: OkHttpClient) : this(okHttpClient, BuildConfig.APP_UPDATES_ENABLED)

    private companion object {
        private const val MAX_RESPONSE_BYTES = 512 * 1024L
    }

    suspend fun fetchLatestRelease(): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        if (!updatesEnabled) {
            return@withContext Result.error("App updates are not available in this build")
        }
        try {
            val updateChannel = AppUpdateChannel.fromCurrentBuild()
            val request = Request.Builder()
                .url(updateChannel.releaseApiUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "IPTV-Player-Update-Checker")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.error("Update check failed: HTTP ${response.code}")
                }

                val body = when (val bodyResult = response.body?.let(::readResponseBodyCapped)) {
                    is Result.Success -> bodyResult.data
                    is Result.Error -> return@withContext Result.error(bodyResult.message, bodyResult.exception)
                    null,
                    Result.Loading -> ""
                }
                if (body.isBlank()) {
                    return@withContext Result.error("Update check failed: empty update manifest")
                }

                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@withContext Result.error("Update check failed: update manifest was invalid")
                return@withContext parseUpdateManifest(json)
            }
        } catch (error: IOException) {
            Result.error("Update check failed: network error", error)
        } catch (error: Exception) {
            Result.error("Update check failed: ${error.message}", error)
        }
    }

    private fun readResponseBodyCapped(body: ResponseBody): Result<String> {
        val contentLength = body.contentLength()
        if (contentLength > MAX_RESPONSE_BYTES) {
            return Result.error("Update check failed: update manifest exceeded 512 KB")
        }

        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytesRead = 0L

        body.byteStream().use { input ->
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                totalBytesRead += bytesRead
                if (totalBytesRead > MAX_RESPONSE_BYTES) {
                    return Result.error("Update check failed: update manifest exceeded 512 KB")
                }

                output.write(buffer, 0, bytesRead)
            }
        }

        return Result.success(output.toString(charset.name()))
    }

    private fun parseUpdateManifest(json: JSONObject): Result<GitHubReleaseInfo> {
        val versionName = json.optString("versionName").trim()
        if (versionName.isBlank()) {
            return Result.error("Update check failed: update manifest has no version")
        }
        val downloadUrl = json.optString("downloadUrl").trim().takeIf(::isHttpsUrl)
        return Result.success(
            GitHubReleaseInfo(
                versionName = versionName,
                versionCode = json.optInt("versionCode", 0).takeIf { it > 0 },
                releaseUrl = UPDATE_RELEASE_PAGE_URL,
                downloadUrl = downloadUrl,
                downloadSha256 = json.optString("sha256").trim().takeIf { SHA256_HEX_REGEX.matches(it) }?.lowercase(),
                releaseNotes = json.optString("releaseNotes").trim(),
                publishedAt = json.optString("publishedAt").takeIf { it.isNotBlank() }
            )
        )
    }

    private fun isHttpsUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        return runCatching {
            val parsed = URI(normalized)
            parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}

internal fun parseReleaseAssetSha256Digest(rawDigest: String): String? {
    val value = rawDigest.trim()
    if (!value.startsWith("sha256:", ignoreCase = true)) return null
    val hex = value.substringAfter(':')
    return hex.takeIf { SHA256_HEX_REGEX.matches(it) }?.lowercase()
}

private val SHA256_HEX_REGEX = Regex("""^[a-fA-F0-9]{64}$""")

enum class AppUpdateChannel(val id: String, val releaseApiUrl: String) {
    // IPTV Player publishes one manifest; beta builds read it too.
    Stable(id = "stable", releaseApiUrl = UPDATE_MANIFEST_URL),
    Beta(id = "beta", releaseApiUrl = UPDATE_MANIFEST_URL);

    companion object {
        fun fromCurrentBuild(): AppUpdateChannel {
            return fromBuildConfig(BuildConfig.APP_UPDATE_CHANNEL, BuildConfig.VERSION_NAME)
        }

        fun fromBuildConfig(channelId: String?, versionName: String): AppUpdateChannel {
            return when {
                channelId.equals(Beta.id, ignoreCase = true) -> Beta
                versionName.contains("-beta", ignoreCase = true) -> Beta
                else -> Stable
            }
        }
    }
}
