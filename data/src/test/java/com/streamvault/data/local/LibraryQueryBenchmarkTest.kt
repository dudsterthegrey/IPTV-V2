package com.streamvault.data.local

import androidx.room.Room
import com.streamvault.data.epg.EpgResolutionEngine
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.domain.model.ContentType
import com.streamvault.domain.model.ProviderType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.SQLiteMode

/**
 * Times the database reads behind the Live TV, guide and home screens on a large Xtream-sized
 * library. Opt-in: run with LIBRARY_BENCHMARK=1. A desktop JVM is roughly 10-20x faster than a
 * Fire TV Stick, so treat tens of milliseconds here as noticeable on a TV stick.
 */
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LibraryQueryBenchmarkTest {
    private lateinit var db: StreamVaultDatabase
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L

    @Before
    fun setUp() {
        assumeTrue(System.getenv("LIBRARY_BENCHMARK") != null)
        db = Room.databaseBuilder(
            RuntimeEnvironment.getApplication(),
            StreamVaultDatabase::class.java,
            "library-benchmark.db"
        ).allowMainThreadQueries().build()
        runBlocking { db.providerDao().insert(ProviderEntity(id = P, name = "Xtream", type = ProviderType.XTREAM_CODES)) }
        val sql = db.openHelper.writableDatabase
        val t = System.nanoTime()
        sql.beginTransaction()
        try {
            sql.execSQL("INSERT OR REPLACE INTO provider_configs (provider_id, type, schema_version, configuration_generation, identity_key, encrypted_config_json, guide_source_policy, channel_logo_source_policy, updated_at) VALUES ($P, 'XTREAM_CODES', 1, 1, 'bench', '{}', 'AUTO', 'SUPPLIER_PREFERRED', 0)")
            sql.execSQL("""
                WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < $LIVE_CATEGORIES)
                INSERT INTO categories (category_id, name, type, provider_id, provider_order, is_adult, is_user_protected, sync_fingerprint)
                SELECT i, 'Category ' || i, 'LIVE', $P, i, 0, 0, '' FROM n
            """)
            sql.execSQL("""
                WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < $CHANNELS)
                INSERT INTO channels (stream_id, name, category_id, stream_url, epg_channel_id, number, provider_id,
                    logical_group_id, error_count, catch_up_supported, catch_up_days, is_adult, is_user_protected, sync_fingerprint, catch_up_correction_seconds)
                SELECT i, 'US: Channel ' || i || CASE WHEN i % 3 = 0 THEN ' HD' ELSE '' END,
                    (i % $LIVE_CATEGORIES) + 1, 'http://x/' || i, 'ch' || i, i, $P,
                    CASE WHEN i % 3 = 0 THEN 'grp' || (i / 3) ELSE '' END, 0, 0, 0, 0, 0, '', 0 FROM n
            """)
            sql.execSQL("INSERT INTO epg_sources (id, name, url, enabled, last_refresh_at, last_success_at, priority, created_at, updated_at, timezone_policy) VALUES (1, 'A', 'http://a', 1, 0, 0, 1, 0, 0, 'REQUIRE_OFFSET')")
            sql.execSQL("INSERT INTO provider_epg_sources (provider_id, epg_source_id, priority, enabled) VALUES ($P, 1, 1, 1)")
            sql.execSQL("""
                WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < $CHANNELS)
                INSERT INTO epg_channels (epg_source_id, xmltv_channel_id, display_name, normalized_name)
                SELECT 1, 'x' || i, 'Channel ' || i, 'channel' || i FROM n WHERE i % 2 = 0
            """)
            sql.execSQL("""
                INSERT INTO channel_epg_mappings (provider_channel_id, provider_id, source_type, epg_source_id, xmltv_channel_id, confidence, matched_at, failed_attempts, is_manual_override, updated_at)
                SELECT id, $P, CASE WHEN stream_id % 2 = 0 THEN 'EXTERNAL' ELSE 'PROVIDER' END,
                    CASE WHEN stream_id % 2 = 0 THEN 1 ELSE NULL END,
                    CASE WHEN stream_id % 2 = 0 THEN 'x' || stream_id ELSE 'ch' || stream_id END, 1.0, 0, 0, 0, 0
                FROM channels
            """)
            // Provider guide: hourly programmes from 12h ago to 5 days ahead (window kept on import).
            sql.execSQL("""
                WITH RECURSIVE h(k) AS (SELECT -12 UNION ALL SELECT k + 1 FROM h WHERE k < ${5 * 24})
                INSERT INTO programs (provider_id, channel_id, title, description, start_time, end_time, lang, has_archive)
                SELECT $P, c.epg_channel_id, 'Show ' || h.k, 'Description of the show', ${now} + h.k * $hour, ${now} + (h.k + 1) * $hour, 'en', 0
                FROM channels c, h WHERE c.stream_id % 2 = 1
            """)
            sql.execSQL("""
                WITH RECURSIVE h(k) AS (SELECT -12 UNION ALL SELECT k + 1 FROM h WHERE k < ${2 * 24})
                INSERT INTO epg_programmes (epg_source_id, xmltv_channel_id, start_time, end_time, title, description, lang)
                SELECT 1, e.xmltv_channel_id, ${now} + h.k * $hour, ${now} + (h.k + 1) * $hour, 'External ' || h.k, '', ''

                FROM epg_channels e, h
            """)
            seed(sql, "categories", VOD_CATEGORIES, mapOf("category_id" to "1000 + i", "name" to "'Movies ' || i", "type" to "'MOVIE'", "provider_id" to "$P"))
            seed(sql, "categories", VOD_CATEGORIES, mapOf("category_id" to "2000 + i", "name" to "'Series ' || i", "type" to "'SERIES'", "provider_id" to "$P"))
            seed(sql, "movies", MOVIES, mapOf(
                "stream_id" to "i", "name" to "'Movie ' || i", "provider_id" to "$P", "category_id" to "1000 + (i % $VOD_CATEGORIES) + 1",
                "added_at" to "$now - i * 60000", "rating" to "(i % 100) / 10.0", "year" to "CAST(1980 + (i % 45) AS TEXT)"
            ))
            seed(sql, "series", SERIES, mapOf(
                "series_id" to "i", "name" to "'Series ' || i", "provider_id" to "$P", "category_id" to "2000 + (i % $VOD_CATEGORIES) + 1",
                "last_modified" to "$now - i * 60000", "rating" to "(i % 100) / 10.0"
            ))
            seed(sql, "title_metadata", MOVIES, mapOf(
                "provider_id" to "$P", "content_type" to "'MOVIE'", "remote_id" to "i",
                "genres" to "CASE WHEN i % 5 = 0 THEN '|comedy|' ELSE '|drama|' END", "countries" to "'|US|'"
            ))
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        sql.execSQL("ANALYZE")
        println("BENCH seed ms=${(System.nanoTime() - t) / 1_000_000}")
        sql.query("SELECT (SELECT COUNT(*) FROM channels), (SELECT COUNT(*) FROM programs), (SELECT COUNT(*) FROM epg_programmes)").use {
            it.moveToFirst(); println("BENCH rows channels=${it.getLong(0)} programs=${it.getLong(1)} epg_programmes=${it.getLong(2)}")
        }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
            RuntimeEnvironment.getApplication().deleteDatabase("library-benchmark.db")
        }
    }

    @Test
    fun timeLiveTvAndGuideQueries() = runBlocking {
        val channelDao = db.channelDao()
        val categoryDao = db.categoryDao()
        time("live categories") { categoryDao.getByProviderAndType(P, ContentType.LIVE.name).first().size }
        time("category counts raw") { channelDao.getRawCategoryCounts(P).first().size }
        time("category counts hide decorative") { channelDao.getCategoryCounts(P).first().size }
        time("category counts grouped") { channelDao.getGroupedCategoryCounts(P).first().size }
        time("all channels page 200") { channelDao.getByProviderBrowsePage(P, 200).first().size }
        time("category page 200") { channelDao.getByCategoryBrowsePage(P, 7, 200).first().size }
        time("all channels offset 10000") { channelDao.getByProviderBrowsePageOffset(P, 50, 10_000).size }

        val engine = EpgResolutionEngine(
            db.channelDao(), db.channelEpgMappingDao(), db.providerEpgSourceDao(), db.epgChannelDao(),
            db.epgProgrammeDao(), db.programDao(), db.providerSnapshotDao()
        )
        val firstPage = channelDao.getByProviderBrowsePage(P, 50).first().map { it.id }
        time("guide programmes 50 channels") {
            engine.getResolvedProgrammes(P, firstPage, now - hour, now + 6 * hour).size
        }
        val lastPage = channelDao.getByProviderBrowsePageOffset(P, 50, 15_000).map { it.id }
        time("guide programmes 50 channels deep") {
            engine.getResolvedProgrammes(P, lastPage, now - hour, now + 6 * hour).size
        }
        time("resolve step: load channels") { channelDao.getEpgResolveRowsByProvider(P).size }
        val epgIds = channelDao.getByProviderSync(P).mapNotNull { it.epgChannelId }
        time("resolve step: channels with programmes") { db.programDao().getProviderChannelIdsWithPrograms(P).size }
        time("resolve step: load guide channels") { db.epgChannelDao().getBySources(listOf(1L)).size }
        val names = channelDao.getByProviderSync(P).map { it.name }
        time("resolve step: normalize names") {
            names.sumOf { com.streamvault.data.epg.EpgNameNormalizer.normalize(it).length +
                com.streamvault.data.epg.EpgNameNormalizer.looseKey(it).length }
        }
        time("resolve step: read mappings") { db.channelEpgMappingDao().getForProvider(P).size }
        val epgChannels = db.epgChannelDao().getBySources(listOf(1L))
        time("resolve step: guide name keys") {
            epgChannels.sumOf { com.streamvault.data.epg.EpgNameNormalizer.looseKey(it.displayName).length +
                (com.streamvault.data.epg.EpgNameNormalizer.countryOfXmltvId(it.xmltvChannelId)?.length ?: 0) }
        }
        val mappings = db.channelEpgMappingDao().getForProvider(P)
        time("resolve step: write mappings") { db.channelEpgMappingDao().replaceForProvider(P, mappings); mappings.size }
        time("guide resolve whole provider") { engine.resolveForProvider(P, emptySet()).toString().length }

        val sql = db.openHelper.readableDatabase
        time("raw sql channels only page 200") {
            sql.query("SELECT * FROM channels WHERE provider_id = $P ORDER BY number LIMIT 200").use { c -> var n = 0; while (c.moveToNext()) n++; n }
        }
        listOf(
            "SELECT c.id FROM channels c WHERE c.provider_id = $P ORDER BY c.number ASC LIMIT 200",
            "SELECT c.id, ec.icon_url FROM channels c " +
                "LEFT JOIN channel_epg_mappings cem ON cem.provider_channel_id = c.id AND cem.provider_id = c.provider_id " +
                "LEFT JOIN epg_channels ec ON ec.epg_source_id = cem.epg_source_id AND ec.xmltv_channel_id = cem.xmltv_channel_id " +
                "WHERE EXISTS (SELECT 1 FROM providers p WHERE p.id = c.provider_id) AND c.provider_id = $P ORDER BY c.number ASC LIMIT 200",
            "SELECT c.id FROM channels c WHERE c.provider_id = $P AND c.category_id = 7 ORDER BY c.number ASC LIMIT 200",
        ).forEach { query ->
            sql.query("EXPLAIN QUERY PLAN $query").use { cursor ->
                val plan = buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }
                println("BENCH plan ${query.take(40)}..${query.takeLast(40)} -> $plan")
            }
        }
    }

    @Test
    fun timeHomeShelfQueries() = runBlocking {
        val movieDao = db.movieDao()
        val seriesDao = db.seriesDao()
        time("movies fresh preview") { movieDao.getFreshPreview(P, 20).first().size }
        time("movies top rated preview") { movieDao.getTopRatedPreview(P, 20).first().size }
        time("movies category preview") { movieDao.getByCategoryPreview(P, 1001, 20).first().size }
        time("movies count") { movieDao.getCount(P).first() }
        time("series fresh preview") { seriesDao.getFreshPreview(P, 20).first().size }
        time("series top rated preview") { seriesDao.getTopRatedPreview(P, 20).first().size }
        time("series category preview") { seriesDao.getByCategoryPreview(P, 2001, 20).first().size }
        time("movie categories") { db.categoryDao().getByProviderAndType(P, ContentType.MOVIE.name).first().size }
        time("movie category counts") { movieDao.getCategoryCounts(P).first().size }
        val sql = db.openHelper.readableDatabase
        val filtered = "SELECT mo.* FROM movies mo LEFT JOIN title_metadata m ON m.provider_id = mo.provider_id " +
            "AND m.content_type = 'MOVIE' AND m.remote_id = mo.stream_id WHERE mo.provider_id = $P AND mo.added_at > 0 " +
            "AND (m.genres LIKE '%|comedy|%') ORDER BY mo.added_at DESC, mo.name ASC, mo.id ASC LIMIT 20"
        time("movies recent filtered by genre") { sql.query(filtered).use { c -> var n = 0; while (c.moveToNext()) n++; n } }
        sql.query("EXPLAIN QUERY PLAN $filtered").use { cursor ->
            val plan = buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }
            println("BENCH plan filtered -> $plan")
        }
    }

    /** Inserts [count] rows, filling required columns the overrides leave out with 0 or ''. */
    private fun seed(sql: androidx.sqlite.db.SupportSQLiteDatabase, table: String, count: Int, overrides: Map<String, String>) {
        val columns = mutableListOf<String>()
        val values = mutableListOf<String>()
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1); val type = c.getString(2).uppercase()
                val notNull = c.getInt(3) == 1; val hasDefault = !c.isNull(4); val pk = c.getInt(5) > 0
                when {
                    name in overrides -> { columns += name; values += overrides.getValue(name) }
                    pk -> Unit
                    notNull && !hasDefault -> { columns += name; values += if (type.contains("INT") || type.contains("REAL")) "0" else "''" }
                }
            }
        }
        sql.execSQL(
            "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < $count) " +
                "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) SELECT ${values.joinToString()} FROM n"
        )
    }

    private suspend fun time(label: String, block: suspend () -> Int) {
        block() // warm up
        val runs = (1..5).map {
            val start = System.nanoTime()
            val size = block()
            (System.nanoTime() - start) / 1_000_000.0 to size
        }
        val median = runs.map { it.first }.sorted()[runs.size / 2]
        println("BENCH %-38s median=%7.1f ms rows=%d".format(label, median, runs.last().second))
    }

    private companion object {
        const val P = 1L
        const val CHANNELS = 20_000
        const val LIVE_CATEGORIES = 400
        const val VOD_CATEGORIES = 150
        const val MOVIES = 40_000
        const val SERIES = 8_000
    }
}
