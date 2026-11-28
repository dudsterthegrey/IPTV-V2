package com.streamvault.player

import android.net.Uri
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.model.PlaybackCompatibilityKey
import com.streamvault.domain.model.StreamInfo
import com.streamvault.domain.repository.PlaybackCompatibilityRepository
import java.io.File
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class Media3PlayerEngineSubtitleOnPrepareTest {
    private val context = RuntimeEnvironment.getApplication()
    private val engine = Media3PlayerEngine(
        context = context,
        okHttpClient = OkHttpClient(),
        playbackCompatibilityRepository = NoOp,
        audioCompatibilityMemoryStore = AudioCompatibilityMemoryStore(context),
        playbackSupportSnapshotStore = PlaybackSupportSnapshotStore(context)
    )
    private val movie = StreamInfo(url = "http://127.0.0.1:9/movie/u/p/3271734.mp4", title = "Movie")
    private val subtitle = Uri.fromFile(File(context.filesDir, "4242.srt").apply { writeText("1\n00:00:01,000 --> 00:00:02,000\nHi\n") })

    @After
    fun tearDown() = engine.release()

    private fun activeSubtitle(): Any? = Media3PlayerEngine::class.java
        .getDeclaredField("activeExternalSubtitle").apply { isAccessible = true }.get(engine)

    @Test
    fun `a subtitle attached for the stream is opened with it`() {
        assertThat(engine.attachExternalSubtitleOnPrepare(movie, subtitle, "en", "English")).isTrue()

        engine.prepare(movie)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(activeSubtitle()).isNotNull()
    }

    @Test
    fun `a subtitle attached for another stream is dropped`() {
        engine.attachExternalSubtitleOnPrepare(movie.copy(url = "http://127.0.0.1:9/movie/u/p/1.mp4"), subtitle, "en")

        engine.prepare(movie)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(activeSubtitle()).isNull()
    }

    private object NoOp : PlaybackCompatibilityRepository {
        override suspend fun getKnownBadRecords(
            deviceFingerprint: String, streamType: String, videoMimeType: String, resolutionBucket: String
        ) = emptyList<com.streamvault.domain.model.PlaybackCompatibilityRecord>()
        override suspend fun recordFailure(key: PlaybackCompatibilityKey, failureType: String, at: Long) = Unit
        override suspend fun recordSuccess(key: PlaybackCompatibilityKey, at: Long) = Unit
        override suspend fun prune(maxRecords: Int, olderThanMs: Long) = Unit
    }
}
