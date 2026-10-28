package com.mustangos.launcher.navigation

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MapTileRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private var clock = 1_700_000_000_000L
    private val key = MapTileKey(4, 5, 6)
    private val calls = mutableListOf<Pair<String, Map<String, String>>>()
    private var reply: (TileCancellation) -> TileHttpResponse = { TileHttpResponse(200, body = png()) }
    private fun repository(max: Long = 24L * 1024 * 1024) = MapTileRepository(temporary.root, "MustangOS/test (Android map)",
        TileHttpTransport { url, headers, token -> calls += url to headers; reply(token) }, { clock }, max)
    private fun png(size: Int = 256) = ByteArray(size).apply {
        byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10).copyInto(this)
        "IHDR".toByteArray().copyInto(this, 12); this[18] = 1; this[22] = 1
    }
    private fun load(repo: MapTileRepository, online: Boolean = true, tile: MapTileKey = key) = repo.load(tile, online, TileCancellation())

    @Test fun fallbackCacheIsAtLeastSevenDaysAndOfflineDoesNotContactServer() {
        val repo = repository()
        assertNull(load(repo, online = false).bytes); assertTrue(calls.isEmpty())
        val first = load(repo)
        assertEquals(clock + MapTileRepository.FALLBACK_TTL_MS, first.expiresAtMs)
        assertEquals("https://tile.openstreetmap.org/4/5/6.png", calls.single().first)
        assertEquals("MustangOS/test (Android map)", calls.single().second["User-Agent"])
        assertFalse(calls.single().second.keys.any { it.equals("Pragma", true) || it.equals("Cache-Control", true) })
        clock += 6L * 24 * 60 * 60 * 1000
        assertTrue(load(repository()).fromCache); assertEquals(1, calls.size)
        clock += 2L * 24 * 60 * 60 * 1000
        val offline = load(repository(), online = false)
        assertNotNull(offline.bytes); assertTrue(offline.stale); assertEquals(1, calls.size)
    }
    @Test fun expiredTilesAreRevalidatedWithEtagAndLastModified() {
        reply = { TileHttpResponse(200, mapOf("Cache-Control" to "max-age=60", "Age" to "10", "ETag" to "\"v1\"",
            "Last-Modified" to "Wed, 15 Nov 2023 00:00:00 GMT"), png()) }
        val repo = repository()
        assertEquals(clock + 50_000, load(repo).expiresAtMs)
        clock += 51_000
        reply = { TileHttpResponse(304, mapOf("cache-control" to "max-age=3600")) }
        val refreshed = load(repo)
        assertNotNull(refreshed.bytes); assertFalse(refreshed.stale)
        assertEquals(clock + 3_600_000, refreshed.expiresAtMs)
        assertEquals("\"v1\"", calls.last().second["If-None-Match"])
        assertEquals("Wed, 15 Nov 2023 00:00:00 GMT", calls.last().second["If-Modified-Since"])
        assertTrue(load(repository()).fromCache); assertEquals(2, calls.size)
    }
    @Test fun dateAndExpiresHonorAgeAndMalformedHeadersUseFallback() {
        // Fixed RFC1123 dates deliberately avoid depending on the local timezone.
        clock = 1_700_000_000_000L
        reply = { TileHttpResponse(200, mapOf("Date" to "Tue, 14 Nov 2023 22:13:20 GMT",
            "Expires" to "Tue, 14 Nov 2023 22:15:20 GMT", "Age" to "30"), png()) }
        assertEquals(clock + 90_000, load(repository()).expiresAtMs)
        reply = { TileHttpResponse(200, mapOf("Cache-Control" to "max-age=oops", "Expires" to "invalid"), png()) }
        assertEquals(clock + MapTileRepository.FALLBACK_TTL_MS, load(repository(), tile = MapTileKey(4, 5, 7)).expiresAtMs)
    }
    @Test fun noStoreDoesNotPersistAndNoCacheNeverServesOfflineWithoutValidation() {
        reply = { TileHttpResponse(200, mapOf("cache-control" to "no-store"), png()) }
        val repo = repository()
        assertFalse(load(repo).cacheable); assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
        assertNull(load(repo, online = false).bytes)
        reply = { TileHttpResponse(200, mapOf("cache-control" to "no-cache", "etag" to "v2"), png()) }
        assertNotNull(load(repo).bytes)
        assertNull(load(repository(), online = false).bytes)
        reply = { TileHttpResponse(304, mapOf("cache-control" to "max-age=120")) }
        assertNotNull(load(repository()).bytes)
        assertEquals("v2", calls.last().second["If-None-Match"])
    }
    @Test fun staleMustRevalidateCannotMasqueradeAsOfflineCurrentMap() {
        reply = { TileHttpResponse(200, mapOf("cache-control" to "max-age=1, must-revalidate"), png()) }
        val repo = repository(); load(repo); clock += 2_000
        assertNull(load(repo, online = false).bytes)
        reply = { throw IOException("fixture offline") }
        assertNull(load(repo).bytes)
    }
    @Test fun invalidBodiesAndUnsafeValidatorHeadersAreNotAccepted() {
        val repo = repository()
        for (invalid in listOf(ByteArray(24), png().apply { this[18] = 2 }, png(MapTileRepository.MAX_TILE_BYTES + 1))) {
            reply = { TileHttpResponse(200, body = invalid) }
            assertNull(load(repo).bytes)
        }
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
        reply = { TileHttpResponse(200, mapOf("cache-control" to "max-age=0", "etag" to "bad\r\nInjected: yes"), png()) }
        load(repo)
        reply = { TileHttpResponse(304) }
        load(repo)
        assertNull(calls.last().second["If-None-Match"])
    }
    @Test fun rateLimitBackoffAppliesAcrossVisibleTilesWithoutBypassingCache() {
        reply = { TileHttpResponse(429, mapOf("Retry-After" to "120")) }
        val repo = repository()
        val denied = load(repo)
        assertEquals(clock + 120_000, denied.retryAtMs)
        load(repo, tile = MapTileKey(4, 5, 7)); assertEquals(1, calls.size)
        clock += 121_000
        reply = { TileHttpResponse(200, body = png()) }
        assertNotNull(load(repo).bytes); assertEquals(2, calls.size)
    }
    @Test fun cancellationDropsResponseAndDoesNotWriteTile() {
        val repo = repository()
        val before = TileCancellation().apply { cancel() }
        assertNull(repo.load(key, true, before).bytes); assertTrue(calls.isEmpty())
        reply = { token -> token.cancel(); TileHttpResponse(200, body = png()) }
        assertNull(load(repo).bytes); assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
        val cancelled = CountDownLatch(1)
        val after = TileCancellation().apply { cancel(); onCancel { cancelled.countDown() } }
        assertTrue(after.isCancelled); assertTrue(cancelled.await(2, TimeUnit.SECONDS))
    }
    @Test fun cancellationMarksImmediatelyAndRunsPotentiallyBlockingCloseHookOffCallerThread() {
        val caller = Thread.currentThread()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val completed = CountDownLatch(1)
        var closerThread: Thread? = null
        val token = TileCancellation().apply { onCancel {
            closerThread = Thread.currentThread(); entered.countDown()
            release.await(2, TimeUnit.SECONDS); completed.countDown()
        } }
        try {
            token.cancel()
            assertTrue(token.isCancelled)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertNotSame(caller, closerThread)
        } finally { release.countDown() }
        assertTrue(completed.await(2, TimeUnit.SECONDS))
    }
    @Test fun boundedCacheEvictsLeastRecentlyUsedOwnedTilesAndKeepsUnrelatedFiles() {
        val unrelated = temporary.newFile("user-data.txt").apply { writeText("untouched") }
        reply = { TileHttpResponse(200, body = png(1024)) }
        val repo = repository(max = 1600)
        load(repo); clock += 1000; load(repo, tile = MapTileKey(4, 5, 7))
        val owned = temporary.root.listFiles().orEmpty().filter { it.extension in listOf("png", "meta") }
        assertTrue(owned.sumOf { it.length() } <= 1600)
        assertTrue(unrelated.exists())
        assertNull(load(repo, online = false).bytes)
        assertNotNull(load(repo, online = false, tile = MapTileKey(4, 5, 7)).bytes)
    }
    @Test fun endpointAndUserAgentMustBeExplicitAndSafe() {
        assertThrows(IllegalArgumentException::class.java) { MapTileRepository(temporary.root, "generic\nInjected: x") }
        assertThrows(IllegalArgumentException::class.java) { MapTileRepository(temporary.root, "MustangOS/test", baseUrl = "http://example.org/") }
        assertThrows(IllegalArgumentException::class.java) { MapTileKey(20, 0, 0) }
    }
}
