package com.streamvault.data.local

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.entity.ChannelEpgMappingEntity
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.domain.model.ProviderType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChannelEpgMappingReplaceTest {
    private lateinit var db: StreamVaultDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StreamVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking { db.providerDao().insert(ProviderEntity(id = 1L, name = "P", type = ProviderType.XTREAM_CODES)) }
    }

    @After
    fun tearDown() = db.close()

    private fun mapping(channelId: Long, xmltvId: String?, updatedAt: Long) = ChannelEpgMappingEntity(
        providerChannelId = channelId,
        providerId = 1L,
        sourceType = if (xmltvId == null) "NONE" else "EXTERNAL",
        epgSourceId = null,
        xmltvChannelId = xmltvId,
        matchType = xmltvId?.let { "EXACT_ID" },
        confidence = if (xmltvId == null) 0f else 1f,
        matchedAt = updatedAt,
        updatedAt = updatedAt
    )

    @Test
    fun `only changed mappings are rewritten and missing channels are removed`() = runBlocking {
        val dao = db.channelEpgMappingDao()
        dao.replaceForProvider(1L, listOf(mapping(1, "a", 100), mapping(2, "b", 100), mapping(3, "c", 100)))
        val before = dao.getForProvider(1L).associateBy { it.providerChannelId }

        dao.replaceForProvider(1L, listOf(mapping(1, "a", 200), mapping(2, "changed", 200)))

        val after = dao.getForProvider(1L).associateBy { it.providerChannelId }
        assertThat(after.keys).containsExactly(1L, 2L)
        // Unchanged match keeps its row as it was.
        assertThat(after.getValue(1L)).isEqualTo(before.getValue(1L))
        assertThat(after.getValue(2L).xmltvChannelId).isEqualTo("changed")
        assertThat(after.getValue(2L).updatedAt).isEqualTo(200L)
    }

    @Test
    fun `an empty list clears the provider`() = runBlocking {
        val dao = db.channelEpgMappingDao()
        dao.replaceForProvider(1L, listOf(mapping(1, "a", 100)))

        dao.replaceForProvider(1L, emptyList())

        assertThat(dao.getForProvider(1L)).isEmpty()
    }
}
