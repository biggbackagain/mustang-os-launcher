package com.mustangos.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Copies a user-selected reference into private storage; the original and its EXIF are never retained. */
class ReferenceImageStore(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.filesDir, "mustang-profile")
    private val image = File(directory, "reference.jpg")
    private val lock = Mutex()
    private val revision = AtomicLong()
    private var committedRevision = 0L // guarded by lock
    fun hasImage() = image.isFile
    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        if (image.isFile) runCatching { BitmapFactory.decodeFile(image.path) }.getOrNull() else null
    }
    suspend fun clear() {
        val request = revision.incrementAndGet()
        withContext(Dispatchers.IO) { lock.withLock {
            if (committedRevision <= request) { image.delete(); committedRevision = request }
        } }
    }
    suspend fun import(uri: Uri): Boolean {
        val request = revision.incrementAndGet()
        return withContext(Dispatchers.IO) {
        if (uri.scheme != "content") return@withContext false
        try {
            app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.declaredLength > MAX_BYTES) return@withContext false
                descriptor.createInputStream().use { importStream(it, request) }
            } ?: false
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { false }
        }
    }
    internal suspend fun importStream(input: InputStream): Boolean = importStream(input, revision.incrementAndGet())
    private suspend fun importStream(input: InputStream, request: Long): Boolean = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        val pending = File(directory, "reference-${UUID.randomUUID()}.pending")
        try {
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                if (revision.get() != request) return@withContext false
                val count = input.read(chunk)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) return@withContext false
                output.write(chunk, 0, count)
            }
            val bytes = output.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384 ||
                bounds.outWidth.toLong() * bounds.outHeight > 64L * 1024 * 1024) return@withContext false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample; inScaled = false
            }) ?: return@withContext false
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                if (rotated !== bitmap) bitmap!!.recycle()
                bitmap = rotated
            }
            currentCoroutineContext().ensureActive()
            lock.withLock {
                if (revision.get() != request) return@withContext false
                directory.mkdirs()
                pending.outputStream().use { check(bitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
                currentCoroutineContext().ensureActive()
                if (revision.get() != request) return@withContext false
                try { Files.move(pending.toPath(), image.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(pending.toPath(), image.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                committedRevision = request
            }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { false }
          catch (_: OutOfMemoryError) { false }
        finally { bitmap?.recycle(); pending.delete() }
    }
    private companion object { const val MAX_BYTES = 12 * 1024 * 1024; const val MAX_SIDE = 1280 }
}
