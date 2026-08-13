package dev.vighnesh.stackpage.image

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Turns a decoded bitmap into compressed bytes at an [Attempt]'s quality and
 * scale. The probe writes into a counting stream so a search over ten
 * attempts never holds ten encodings in memory, only the byte counts.
 */
object Encoder {

    /** Counts bytes and drops them; all the search needs is the size. */
    private class CountingStream : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            count += len
        }
    }

    /** Size in bytes this attempt would produce, without keeping the encoding. */
    fun probe(
        bitmap: Bitmap,
        attempt: Attempt,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ): Long = withScaled(bitmap, attempt.scalePercent) { scaled ->
        val stream = CountingStream()
        scaled.compress(format, attempt.quality, stream)
        stream.count
    }

    /** The real encoding for the chosen attempt. */
    fun encode(
        bitmap: Bitmap,
        attempt: Attempt,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ): ByteArray = withScaled(bitmap, attempt.scalePercent) { scaled ->
        val out = ByteArrayOutputStream()
        scaled.compress(format, attempt.quality, out)
        out.toByteArray()
    }

    private inline fun <T> withScaled(bitmap: Bitmap, scalePercent: Int, block: (Bitmap) -> T): T {
        if (scalePercent >= 100) return block(bitmap)
        val w = (bitmap.width * scalePercent / 100).coerceAtLeast(1)
        val h = (bitmap.height * scalePercent / 100).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        try {
            return block(scaled)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}
