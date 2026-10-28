package com.mustangos.launcher.navigation

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class MapTileKey(val zoom: Int, val x: Int, val y: Int) {
    init { require(zoom in 0..19 && x in 0 until (1 shl zoom) && y in 0 until (1 shl zoom)) }
}
data class MapTileData(
    val bytes: ByteArray? = null,
    val expiresAtMs: Long = 0,
    val cacheable: Boolean = true,
    val fromCache: Boolean = false,
    val stale: Boolean = false,
    val retryAtMs: Long = 0
)
class TileCancellation {
    private val cancelled = AtomicBoolean(false)
    private val hooks = mutableListOf<() -> Unit>()
    val isCancelled: Boolean get() = cancelled.get() || Thread.currentThread().isInterrupted
    fun onCancel(block: () -> Unit) {
        val run = synchronized(hooks) { if (cancelled.get()) true else { hooks += block; false } }
        if (run) dispatch(listOf(block))
    }
    fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            val closing = synchronized(hooks) { hooks.toList().also { hooks.clear() } }
            if (closing.isNotEmpty()) dispatch(closing)
        }
    }
    private fun dispatch(closing: List<() -> Unit>) { closer.execute { closing.forEach { runCatching(it) } } }
    companion object {
        // disconnect() may perform vendor networking I/O; cancellation flags change immediately on Main.
        // Only two HTTP connections can exist. A full close queue falls back to the transport's bounded timeout/finally.
        private val closer = ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, ArrayBlockingQueue(64), { task ->
            Thread(task, "mustang-map-close").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }, ThreadPoolExecutor.DiscardPolicy()).apply { allowCoreThreadTimeOut(true) }
    }
}
interface MapTileSource {
    /** Blocking I/O: the map must call this only from its bounded worker executor. */
    fun load(key: MapTileKey, networkEnabled: Boolean, cancellation: TileCancellation): MapTileData
    fun cancelAll()
    fun discard(key: MapTileKey) = Unit
}
data class TileHttpResponse(val code: Int, val headers: Map<String, String> = emptyMap(), val body: ByteArray? = null)
fun interface TileHttpTransport {
    fun request(url: String, headers: Map<String, String>, cancellation: TileCancellation): TileHttpResponse
}

/** Standard HTTPS endpoint; no redirects, generic UA, unbounded bodies or cache-bypassing headers. */
class UrlTileTransport : TileHttpTransport {
    override fun request(url: String, headers: Map<String, String>, cancellation: TileCancellation): TileHttpResponse {
        val target = URL(url)
        require(target.protocol == "https")
        if (cancellation.isCancelled) throw IOException("Cancelled")
        val connection = target.openConnection() as HttpURLConnection
        cancellation.onCancel { connection.disconnect() }
        try {
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            headers.forEach(connection::setRequestProperty)
            val code = connection.responseCode
            val responseHeaders = connection.headerFields.filterKeys { it != null }.map { (key, values) ->
                key.lowercase(Locale.ROOT) to values.joinToString(",")
            }.toMap()
            if (code != HttpURLConnection.HTTP_OK) return TileHttpResponse(code, responseHeaders)
            if (connection.contentLengthLong > MapTileRepository.MAX_TILE_BYTES) throw IOException("Tile too large")
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (!cancellation.isCancelled) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MapTileRepository.MAX_TILE_BYTES) throw IOException("Tile too large")
                    output.write(buffer, 0, count)
                }
                if (cancellation.isCancelled) throw IOException("Cancelled")
                output.toByteArray()
            }
            return TileHttpResponse(code, responseHeaders, bytes)
        } finally { connection.disconnect() }
    }
}

/** Only explicitly requested visible tiles are loaded. Cache files contain tiles/HTTP metadata, no GPS trace. */
class MapTileRepository(
    private val directory: File,
    private val userAgent: String,
    private val transport: TileHttpTransport = UrlTileTransport(),
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = 24L * 1024 * 1024,
    private val baseUrl: String = "https://tile.openstreetmap.org/"
) : MapTileSource {
    private data class Entry(val bytes: ByteArray, val metadata: Metadata)
    private data class Metadata(
        val expires: Long, val mustRevalidate: Boolean, val noCache: Boolean,
        val etag: String?, val modified: String?, val cacheControl: String?, val expiresHeader: String?
    )
    private val active = ConcurrentHashMap.newKeySet<TileCancellation>()
    private val cacheLock = CACHE_LOCKS.computeIfAbsent(directory.absolutePath) { Any() }
    private val prefix = MessageDigest.getInstance("SHA-256").digest(baseUrl.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
    @Volatile private var backoffUntil = 0L
    init {
        require(userAgent.isNotBlank() && userAgent.length <= 256 && '\r' !in userAgent && '\n' !in userAgent)
        require(URL(baseUrl).protocol == "https" && baseUrl.endsWith('/'))
        require(maxBytes > 0)
    }
    override fun cancelAll() { active.forEach(TileCancellation::cancel) }
    override fun discard(key: MapTileKey) = remove(key)
    override fun load(key: MapTileKey, networkEnabled: Boolean, cancellation: TileCancellation): MapTileData {
        if (cancellation.isCancelled) return MapTileData()
        active += cancellation
        try {
            val time = now()
            val cached = read(key)
            if (cancellation.isCancelled) return MapTileData()
            if (cached != null && !cached.metadata.noCache && time < cached.metadata.expires) return cached.result(time)
            if (!networkEnabled || time < backoffUntil) return offline(cached, time, backoffUntil)
            val requestHeaders = linkedMapOf("User-Agent" to userAgent, "Accept" to "image/png")
            cached?.metadata?.etag?.let { requestHeaders["If-None-Match"] = it }
            cached?.metadata?.modified?.let { requestHeaders["If-Modified-Since"] = it }
            val response = try { transport.request("$baseUrl${key.zoom}/${key.x}/${key.y}.png", requestHeaders, cancellation) }
                catch (_: Exception) { return if (cancellation.isCancelled) MapTileData() else offline(cached, time, time + 30_000) }
            if (cancellation.isCancelled) return MapTileData()
            val headers = response.headers.mapKeys { it.key.lowercase(Locale.ROOT) }
            if (response.code == 304 && cached != null) {
                val merged = linkedMapOf<String, String>()
                cached.metadata.cacheControl?.let { merged["cache-control"] = it }
                cached.metadata.expiresHeader?.let { merged["expires"] = it }
                cached.metadata.etag?.let { merged["etag"] = it }
                cached.metadata.modified?.let { merged["last-modified"] = it }
                merged.putAll(headers)
                val policy = metadata(merged, now())
                if (policy.first) { remove(key); return MapTileData(cached.bytes, policy.second.expires, cacheable = false) }
                write(key, cached.bytes, policy.second, cancellation)
                return Entry(cached.bytes, policy.second).result(now())
            }
            if (response.code == 200 && validPng(response.body)) {
                val bytes = response.body!!
                val policy = metadata(headers, now())
                if (policy.first) remove(key) else write(key, bytes, policy.second, cancellation)
                return if (cancellation.isCancelled) MapTileData() else MapTileData(bytes, policy.second.expires, cacheable = !policy.first)
            }
            val retry = if (response.code in listOf(403, 429, 503)) {
                val at = retryAt(headers["retry-after"], now()) ?: (now() + 60_000)
                backoffUntil = maxOf(backoffUntil, at); backoffUntil
            } else now() + 30_000
            return offline(cached, now(), retry)
        } finally { active -= cancellation }
    }

    private fun Entry.result(time: Long) = MapTileData(bytes, metadata.expires, fromCache = true, stale = time >= metadata.expires)
    private fun offline(entry: Entry?, time: Long, retry: Long): MapTileData =
        if (entry != null && !entry.metadata.mustRevalidate && !entry.metadata.noCache)
            entry.result(time).copy(retryAtMs = maxOf(retry, time + 30_000))
        else MapTileData(retryAtMs = maxOf(retry, time + 30_000))

    private fun file(key: MapTileKey) = File(directory, "$prefix-${key.zoom}_${key.x}_${key.y}.png")
    private fun metaFile(tile: File) = File(directory, tile.name.removeSuffix(".png") + ".meta")
    private fun read(key: MapTileKey): Entry? = synchronized(cacheLock) {
        val tile = file(key); val meta = metaFile(tile)
        try {
            if (tile.length() !in 24..MAX_TILE_BYTES.toLong() || meta.length() !in 1..8192) return@synchronized null
            val properties = Properties().apply { meta.inputStream().use(::load) }
            val bytes = tile.readBytes()
            if (!validPng(bytes)) { tile.delete(); meta.delete(); return@synchronized null }
            val expires = properties.getProperty("expires")?.toLongOrNull() ?: return@synchronized null
            val entry = Entry(bytes, Metadata(expires, properties.getProperty("mustRevalidate") == "true",
                properties.getProperty("noCache") == "true", validator(properties.getProperty("etag")),
                validator(properties.getProperty("modified")), safeHeader(properties.getProperty("cacheControl")),
                safeHeader(properties.getProperty("expiresHeader"))))
            tile.setLastModified(now())
            entry
        } catch (_: Exception) { null }
    }
    private fun remove(key: MapTileKey) = synchronized(cacheLock) { file(key).let { it.delete(); metaFile(it).delete() }; Unit }
    private fun write(key: MapTileKey, bytes: ByteArray, value: Metadata, cancellation: TileCancellation) = synchronized(cacheLock) {
        if (cancellation.isCancelled) return@synchronized
        val tile = file(key); val meta = metaFile(tile)
        val suffix = ".${UUID.randomUUID()}.tmp"
        val tempTile = File(directory, tile.name + suffix); val tempMeta = File(directory, meta.name + suffix)
        try {
            if (!directory.exists() && !directory.mkdirs()) return@synchronized
            tempTile.writeBytes(bytes)
            val properties = Properties().apply {
                setProperty("expires", value.expires.toString()); setProperty("mustRevalidate", value.mustRevalidate.toString())
                setProperty("noCache", value.noCache.toString())
                value.etag?.let { setProperty("etag", it) }; value.modified?.let { setProperty("modified", it) }
                value.cacheControl?.let { setProperty("cacheControl", it) }; value.expiresHeader?.let { setProperty("expiresHeader", it) }
            }
            tempMeta.outputStream().use { properties.store(it, "Map tile HTTP cache") }
            if (cancellation.isCancelled) return@synchronized
            Files.move(tempTile.toPath(), tile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(tempMeta.toPath(), meta.toPath(), StandardCopyOption.REPLACE_EXISTING)
            tile.setLastModified(now())
            evict()
        } catch (_: Exception) { /* Cache failure never prevents displaying a valid network response. */ }
        finally { tempTile.delete(); tempMeta.delete() }
    }
    private fun evict() {
        val tiles = directory.listFiles()?.filter { it.isFile && it.name.matches(CACHE_FILE) }?.sortedBy(File::lastModified).orEmpty()
        var size = tiles.sumOf { it.length() + metaFile(it).length() }
        for (tile in tiles) {
            if (size <= maxBytes) break
            val meta = metaFile(tile); val bytes = tile.length() + meta.length()
            tile.delete(); meta.delete(); size -= bytes
        }
    }

    private fun metadata(headers: Map<String, String>, time: Long): Pair<Boolean, Metadata> {
        val control = safeHeader(headers["cache-control"])
        val directives = control.orEmpty().lowercase(Locale.ROOT).split(',').map(String::trim)
        val noStore = "no-store" in directives
        val noCache = directives.any { it == "no-cache" || it.startsWith("no-cache=") }
        val mustRevalidate = "must-revalidate" in directives
        val maxAge = directives.firstOrNull { it.startsWith("max-age=") }?.substringAfter('=')?.trim('"')?.toLongOrNull()?.takeIf { it >= 0 }
        val age = headers["age"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        val apparentAge = httpDate(headers["date"])?.let { (time - it).coerceAtLeast(0) } ?: 0
        val currentAge = maxOf(millis(age), apparentAge)
        val expiresHeader = safeHeader(headers["expires"])
        val expires = when {
            noCache -> time
            maxAge != null -> saturatingAdd(time, (millis(maxAge) - currentAge).coerceAtLeast(0))
            httpDate(expiresHeader) != null -> {
                val expiresTime = httpDate(expiresHeader)!!
                val date = httpDate(headers["date"])
                if (date != null) saturatingAdd(time, (expiresTime - date - currentAge).coerceAtLeast(0)) else maxOf(time, expiresTime - millis(age))
            }
            else -> saturatingAdd(time, FALLBACK_TTL_MS)
        }
        return noStore to Metadata(expires, mustRevalidate, noCache, validator(headers["etag"]),
            validator(headers["last-modified"]), control, expiresHeader)
    }
    private fun retryAt(header: String?, time: Long): Long? = header?.toLongOrNull()?.takeIf { it >= 0 }
        ?.let { saturatingAdd(time, millis(it)) } ?: httpDate(header)?.takeIf { it > time }
    private fun httpDate(value: String?): Long? = value?.let { raw -> runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT"); isLenient = false }.parse(raw)?.time
    }.getOrNull() }
    private fun safeHeader(value: String?): String? = value?.takeIf { it.length <= 2048 && '\r' !in it && '\n' !in it }
    private fun validator(value: String?): String? = safeHeader(value)?.takeIf { it.length <= 1024 }
    private fun millis(seconds: Long) = seconds.coerceIn(0, Long.MAX_VALUE / 1000) * 1000
    private fun saturatingAdd(first: Long, second: Long) = if (second > 0 && first > Long.MAX_VALUE - second) Long.MAX_VALUE else first + second

    companion object {
        const val MAX_TILE_BYTES = 1024 * 1024
        const val FALLBACK_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private val CACHE_LOCKS = ConcurrentHashMap<String, Any>()
        private val CACHE_FILE = Regex("[0-9a-f]{12}-[0-9]+_[0-9]+_[0-9]+\\.png")
        fun validPng(bytes: ByteArray?): Boolean {
            if (bytes == null || bytes.size !in 24..MAX_TILE_BYTES) return false
            val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
            if (!bytes.copyOfRange(0,8).contentEquals(signature) || bytes.copyOfRange(12,16).toString(Charsets.US_ASCII) != "IHDR") return false
            fun dimension(offset: Int) = ((bytes[offset].toInt() and 255) shl 24) or ((bytes[offset+1].toInt() and 255) shl 16) or
                ((bytes[offset+2].toInt() and 255) shl 8) or (bytes[offset+3].toInt() and 255)
            return dimension(16) == 256 && dimension(20) == 256
        }
    }
}
