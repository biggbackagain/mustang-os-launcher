package com.mustangos.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.mustangos.core.MustangBody
import com.mustangos.core.MustangPaint
import com.mustangos.core.MustangProfile
import com.mustangos.core.MustangVersion
import com.mustangos.core.PhotoReference
import com.mustangos.core.PhotoReferenceMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class PhotoCredits(
    val referenceId: String,
    val title: String,
    val author: String,
    val license: String,
    val licenseUrl: String,
    val source: String,
    val changes: String
)

/** Bitmap is shared with the catalog cache; callers must not recycle it. Null is the bundled stock. */
data class PhotoPresentation(
    val bitmap: Bitmap?,
    val referenceCaption: String,
    val description: String,
    val usesCalibratedStock: Boolean,
    val credits: PhotoCredits?,
    val referenceId: String
)

/** Bundled references only. New JSON entries need no code changes; no URI, network or private file is opened. */
class CarPhotoCatalog(
    context: Context,
    assetReader: ((String) -> InputStream)? = null
) {
    private val assets = context.applicationContext.assets
    private val openAsset: (String) -> InputStream = assetReader ?: { assets.open(it) }
    private data class Entry(val reference: PhotoReference, val credits: PhotoCredits, val sha256: String?)
    private val lock = Mutex()
    private var entries: List<Entry>? = null
    private val images = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(profile: MustangProfile): PhotoPresentation = withContext(Dispatchers.IO) {
        lock.withLock {
            val catalog = readCatalog()
            val reference = PhotoReferenceMatcher.select(profile, catalog.map { it.reference })
            if (reference.id == PhotoReferenceMatcher.stock.id) return@withLock presentation(PhotoReferenceMatcher.stock, profile, null, null)
            val entry = catalog.firstOrNull { it.reference.id == reference.id }
                ?: return@withLock presentation(PhotoReferenceMatcher.stock, profile, null, null)
            val cached = images.get(reference.id)?.takeUnless { it.isRecycled }
            if (cached != null) return@withLock presentation(reference, profile, cached, entry.credits)
            var bitmap: Bitmap? = null
            var cachedResult = false
            try {
                val bytes = read(requireNotNull(reference.asset), MAX_IMAGE_BYTES)
                if (entry.sha256 != null && hash(bytes) != entry.sha256) return@withLock presentation(PhotoReferenceMatcher.stock, profile, null, null)
                currentCoroutineContext().ensureActive()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384 ||
                    bounds.outWidth.toLong() * bounds.outHeight > 64L * 1024 * 1024)
                    return@withLock presentation(PhotoReferenceMatcher.stock, profile, null, null)
                var sample = 1
                while ((maxOf(bounds.outWidth, bounds.outHeight) + sample - 1) / sample > MAX_SIDE) sample *= 2
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample; inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: return@withLock presentation(PhotoReferenceMatcher.stock, profile, null, null)
                if (maxOf(bitmap!!.width, bitmap!!.height) > MAX_SIDE) {
                    val original = bitmap!!
                    val scale = MAX_SIDE.toFloat() / maxOf(original.width, original.height)
                    bitmap = Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1),
                        (original.height * scale).toInt().coerceAtLeast(1), true)
                    if (bitmap !== original) original.recycle()
                }
                currentCoroutineContext().ensureActive()
                images.put(reference.id, bitmap!!); cachedResult = true
                presentation(reference, profile, bitmap, entry.credits)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { presentation(PhotoReferenceMatcher.stock, profile, null, null) }
              catch (_: OutOfMemoryError) { presentation(PhotoReferenceMatcher.stock, profile, null, null) }
            finally { if (!cachedResult) bitmap?.recycle() }
        }
    }

    suspend fun credits(): List<PhotoCredits> = withContext(Dispatchers.IO) { lock.withLock { readCatalog().map { it.credits } } }

    private suspend fun readCatalog(): List<Entry> {
        entries?.let { return it }
        val loaded = try {
            val array = JSONArray(read("cars/catalog.json", 512 * 1024).toString(Charsets.UTF_8))
            (0 until minOf(array.length(), 512)).mapNotNull { index ->
                runCatching { parse(array.getJSONObject(index)) }.getOrNull()
            }.distinctBy { it.reference.id }
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { emptyList() }
          catch (_: OutOfMemoryError) { emptyList() }
        entries = loaded
        return loaded
    }

    private fun parse(json: JSONObject): Entry {
        fun text(key: String): String = json.getString(key).take(4096)
        fun optional(key: String): String? = if (!json.has(key) || json.isNull(key)) null else json.getString(key)
        fun version(): MustangVersion? = optional("version")?.let { value -> MustangVersion.entries.first { it.name == value } }
        fun body(): MustangBody? = optional("body")?.let { value -> MustangBody.entries.first { it.name == value } }
        fun paint(): MustangPaint? = optional("paint")?.let { value -> MustangPaint.entries.first { it.name == value } }
        val id = text("id").also { require(it.length in 1..128 && it != PhotoReferenceMatcher.stock.id) }
        val asset = text("asset").also {
            require(it.length <= 256 && it.startsWith("cars/") && '\\' !in it && ':' !in it && it.split('/').none { part -> part in listOf("", ".", "..") })
        }
        val reference = PhotoReference(id, asset, json.getInt("yearFrom"), json.getInt("yearTo"), json.getInt("referenceYear"), version(), body(), paint())
        val credit = PhotoCredits(id, text("title"), text("author"), text("license"), text("licenseUrl"), text("source"), text("changes"))
        require(credit.source.startsWith("https://") && credit.licenseUrl.startsWith("https://"))
        val checksum = optional("sha256")?.lowercase()?.also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        return Entry(reference, credit, checksum)
    }
    private suspend fun read(path: String, maximum: Int): ByteArray = openAsset(path).use { input ->
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(chunk)
            if (count < 0) break
            check(output.size() + count <= maximum)
            output.write(chunk, 0, count)
        }
        output.toByteArray()
    }
    private fun presentation(reference: PhotoReference, profile: MustangProfile, bitmap: Bitmap?, credits: PhotoCredits?): PhotoPresentation {
        val stock = reference.id == PhotoReferenceMatcher.stock.id
        val actual = listOfNotNull("Mustang ${reference.referenceYear}", reference.version?.label, reference.body?.label, reference.paint?.label).joinToString(" · ")
        val exact = profile.year == reference.referenceYear && profile.version == reference.version &&
            PhotoReferenceMatcher.equivalentBody(reference.body, profile.body) && reference.paint == profile.paint
        val prefix = when { stock -> "Imagen incluida"; exact -> "Foto de referencia"; else -> "Referencia aproximada" }
        return PhotoPresentation(bitmap, "$prefix · $actual", "Fotografía de referencia: $actual", stock, credits, reference.id)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object { const val MAX_SIDE = 800; const val MAX_IMAGE_BYTES = 4 * 1024 * 1024 }
}
