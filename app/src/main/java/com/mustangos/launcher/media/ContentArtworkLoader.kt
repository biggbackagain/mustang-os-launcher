package com.mustangos.launcher.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

internal enum class ArtworkStatus(val label: String) {
    NONE("La fuente no publicó portada"),
    BITMAP("Portada publicada por la sesión"),
    LOADING("Cargando portada de la sesión"),
    CONTENT("Portada de contenido de la sesión"),
    REJECTED("Portada rechazada: se requiere contenido local"),
    PERMISSION_DENIED("Portada sin permiso de lectura"),
    TOO_LARGE("Portada supera el límite de tamaño"),
    INVALID_IMAGE("Portada no decodificable"),
    UNAVAILABLE("Portada no disponible"),
    TIMED_OUT("Portada agotó el tiempo de lectura")
}

internal data class ArtworkResult(val bitmap: Bitmap? = null, val status: ArtworkStatus)

/** Only receives standard artwork URIs from the selected MediaMetadata. No URI or exception is logged. */
internal class ContentArtworkLoader(private val resolver: ContentResolver) {
    suspend fun load(uri: Uri): ArtworkResult {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority.isNullOrBlank())
            return ArtworkResult(status = ArtworkStatus.REJECTED)
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val request = ReadRequest()
                continuation.invokeOnCancellation { request.cancel() }
                // A single bounded read/decode runs at a time, including across stopped owners.
                // A provider that ignores cancellation cannot grow a pool of image allocations.
                reader.dispatch(EmptyCoroutineContext) {
                    if (!continuation.isActive) return@dispatch
                    val result = try {
                        read(uri, request)
                    } catch (_: SecurityException) {
                        ArtworkResult(status = ArtworkStatus.PERMISSION_DENIED)
                    } catch (_: OperationCanceledException) {
                        ArtworkResult(status = ArtworkStatus.UNAVAILABLE)
                    } catch (_: IOException) {
                        ArtworkResult(status = ArtworkStatus.UNAVAILABLE)
                    } catch (_: RuntimeException) {
                        ArtworkResult(status = ArtworkStatus.UNAVAILABLE)
                    } catch (_: OutOfMemoryError) {
                        ArtworkResult(status = ArtworkStatus.TOO_LARGE)
                    } finally { request.clear() }
                    continuation.resume(result) { _, abandoned, _ -> abandoned.bitmap?.recycle() }
                }
            }
        } ?: ArtworkResult(status = ArtworkStatus.TIMED_OUT)
    }

    private fun read(uri: Uri, request: ReadRequest): ArtworkResult {
        request.check()
        val descriptor = resolver.openAssetFileDescriptor(uri, "r", request.signal)
            ?: return ArtworkResult(status = ArtworkStatus.UNAVAILABLE)
        descriptor.use {
            request.track(it)
            if (it.declaredLength > MAX_BYTES) return ArtworkResult(status = ArtworkStatus.TOO_LARGE)
            val capacity = if (it.declaredLength in 1..MAX_BYTES.toLong()) it.declaredLength.toInt() else MAX_BYTES
            val bytes = ByteArray(capacity)
            val size = it.createInputStream().use { stream ->
                request.track(stream)
                var count = 0
                while (count < bytes.size) {
                    request.check()
                    val read = stream.read(bytes, count, minOf(16 * 1024, bytes.size - count))
                    if (read < 0) break
                    if (read == 0) continue
                    count += read
                }
                request.check()
                // One sentinel byte distinguishes an exactly full image from an oversized stream.
                if (count == MAX_BYTES && stream.read() != -1) return ArtworkResult(status = ArtworkStatus.TOO_LARGE)
                count
            }
            request.check()
            if (size == 0) return ArtworkResult(status = ArtworkStatus.INVALID_IMAGE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ArtworkResult(status = ArtworkStatus.INVALID_IMAGE)
            if (bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION ||
                bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) return ArtworkResult(status = ArtworkStatus.TOO_LARGE)
            var sample = 1
            val largest = maxOf(bounds.outWidth, bounds.outHeight)
            while ((largest + sample - 1) / sample > MAX_SIDE) sample *= 2
            request.check()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, size, BitmapFactory.Options().apply {
                inSampleSize = sample; inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return ArtworkResult(status = ArtworkStatus.INVALID_IMAGE)
            if (request.cancelled.get()) { bitmap.recycle(); throw CancellationException() }
            val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== bitmap) bitmap.recycle() }
            } else bitmap
            if (request.cancelled.get()) { scaled.recycle(); throw CancellationException() }
            return ArtworkResult(scaled, ArtworkStatus.CONTENT)
        }
    }

    private class ReadRequest {
        val signal = CancellationSignal()
        val cancelled = AtomicBoolean(false)
        private val active = AtomicReference<Closeable?>()
        fun track(resource: Closeable) {
            active.set(resource)
            if (cancelled.get()) { runCatching { resource.close() }; throw CancellationException() }
        }
        fun check() { if (cancelled.get()) throw CancellationException(); signal.throwIfCanceled() }
        fun cancel() {
            cancelled.set(true)
            // Cancellation may be requested on Main. Provider IPC and close stay on IO.
            Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                runCatching { signal.cancel() }
                runCatching { active.getAndSet(null)?.close() }
            }
        }
        fun clear() { active.set(null) }
    }

    private companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        const val MAX_SIDE = 384
        const val MAX_DIMENSION = 16384
        const val MAX_PIXELS = 64L * 1024 * 1024
        const val READ_TIMEOUT_MS = 3000L
        val reader = Dispatchers.IO.limitedParallelism(1)
    }
}
