package com.streamvault.data.repository

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.dao.SavedStreamDao
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.SavedStream
import com.streamvault.domain.model.SavedStreamKind
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking

class SavedStreamRepositoryImplTest {

    private val server = MockWebServer()
    private val dao: SavedStreamDao = mock()
    private val repository = SavedStreamRepositoryImpl(dao, OkHttpClient())

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `plain text response uses its first line`() {
        assertThat(parseStreamApiResponse("\n  https://cdn.example/live.m3u8?token=1  \nignored"))
            .isEqualTo("https://cdn.example/live.m3u8?token=1")
    }

    @Test
    fun `json response reads url-like fields, including one level down`() {
        assertThat(parseStreamApiResponse("""{"url":"https://a.example/1.m3u8"}""")).isEqualTo("https://a.example/1.m3u8")
        assertThat(parseStreamApiResponse("""{"stream_url":"rtmp://b.example/live"}""")).isEqualTo("rtmp://b.example/live")
        assertThat(parseStreamApiResponse("""{"data":{"streamUrl":"https://c.example/x.ts"}}""")).isEqualTo("https://c.example/x.ts")
    }

    @Test
    fun `responses without a playable url are rejected`() {
        assertThat(parseStreamApiResponse("")).isNull()
        assertThat(parseStreamApiResponse("""{"status":"ok"}""")).isNull()
        assertThat(parseStreamApiResponse("javascript:alert(1)")).isNull()
        assertThat(parseStreamApiResponse("<html>error</html>")).isNull()
    }

    @Test
    fun `stream api endpoints must be http or https`() {
        assertThat(isAllowedSavedStreamUrl("https://api.example/next", SavedStreamKind.STREAM_API)).isTrue()
        assertThat(isAllowedSavedStreamUrl("rtsp://cam.example/1", SavedStreamKind.STREAM_API)).isFalse()
        assertThat(isAllowedSavedStreamUrl("rtsp://cam.example/1", SavedStreamKind.DIRECT_URL)).isTrue()
        assertThat(isAllowedSavedStreamUrl("file:///sdcard/x.mp4", SavedStreamKind.DIRECT_URL)).isFalse()
        assertThat(isAllowedSavedStreamUrl("https://", SavedStreamKind.DIRECT_URL)).isFalse()
    }

    @Test
    fun `stream api is fetched on resolve`() = runTest {
        server.enqueue(MockResponse().setBody("""{"url":"https://cdn.example/fresh.m3u8"}"""))
        server.start()
        val stream = SavedStream(name = "News", kind = SavedStreamKind.STREAM_API, url = server.url("/next").toString())

        val resolved = repository.resolvePlaybackUrl(stream)

        assertThat((resolved as Result.Success).data).isEqualTo("https://cdn.example/fresh.m3u8")
        assertThat(server.takeRequest().path).isEqualTo("/next")
    }

    @Test
    fun `stream api http errors fail the resolve`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        val stream = SavedStream(name = "News", kind = SavedStreamKind.STREAM_API, url = server.url("/next").toString())

        assertThat(repository.resolvePlaybackUrl(stream)).isInstanceOf(Result.Error::class.java)
    }

    @Test
    fun `direct urls resolve to themselves without a request`() = runTest {
        val stream = SavedStream(name = "Cam", kind = SavedStreamKind.DIRECT_URL, url = " https://cam.example/live.m3u8 ")

        assertThat((repository.resolvePlaybackUrl(stream) as Result.Success).data).isEqualTo("https://cam.example/live.m3u8")
    }

    @Test
    fun `invalid streams are not saved`() = runTest {
        val result = repository.save(SavedStream(name = " ", kind = SavedStreamKind.DIRECT_URL, url = "https://x.example/a"))

        assertThat(result).isInstanceOf(Result.Error::class.java)
        verifyBlocking(dao, never()) { upsert(any()) }
    }
}
