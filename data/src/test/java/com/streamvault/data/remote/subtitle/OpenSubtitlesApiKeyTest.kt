package com.streamvault.data.remote.subtitle

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.streamvault.domain.model.Result
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class OpenSubtitlesApiKeyTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(builtInKey: String, userKey: String?) = OpenSubtitlesApi(
        okHttpClient = OkHttpClient(),
        gson = Gson(),
        apiKey = builtInKey,
        userApiKeySource = { userKey },
    )

    private fun host() = server.url("/").toString().trimEnd('/')

    @Test
    fun `key entered in settings wins over the built-in key`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        server.start()

        api(builtInKey = "built-in", userKey = " my-key ").languages(host())

        assertThat(server.takeRequest().getHeader("Api-Key")).isEqualTo("my-key")
    }

    @Test
    fun `built-in key is used when none was entered`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        server.start()

        api(builtInKey = "built-in", userKey = null).languages(host())

        assertThat(server.takeRequest().getHeader("Api-Key")).isEqualTo("built-in")
        assertThat(api(builtInKey = "built-in", userKey = null).hasBuiltInApiKey).isTrue()
    }

    @Test
    fun `requests fail without any key`() = runBlocking {
        val result = api(builtInKey = "", userKey = null).languages(host())

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(api(builtInKey = " ", userKey = null).hasBuiltInApiKey).isFalse()
    }

    @Test
    fun `a key being checked is sent instead of the saved one, and a 403 marks it rejected`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.start()

        val result = api(builtInKey = "built-in", userKey = "saved").languages(host(), apiKeyOverride = "candidate")

        assertThat(server.takeRequest().getHeader("Api-Key")).isEqualTo("candidate")
        val error = (result as Result.Error).exception as OpenSubtitlesApiException
        assertThat(error.isApiKeyRejected).isTrue()
    }
}
