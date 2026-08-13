package dev.vighnesh.stackpage.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Decoding a user-picked image safely.
 *
 * Two things go wrong here if you decode naively, and both are guaranteed to
 * happen with real phone photos:
 *
 *  1. A 50MP image decoded at full resolution is ~200MB of ARGB_8888 and will
 *     OOM the app. Everything is downsampled to a bound.
 *  2. Most phone cameras store the sensor's native orientation and put the
 *     real one in EXIF. Without applying it, portrait photos export sideways.
 */
object ImageSource {

    /**
     * Longest-edge bound for the bitmap handed to the PDF. 2400px is roughly
     * 300dpi across an A4 short side, which is past what anyone prints or
     * reads on screen, and keeps a single page under ~25MB while decoding.
     */
    const val EXPORT_MAX_EDGE = 2400

    /** Dimensions after EXIF rotation, without decoding any pixels. */
    fun readSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        val quarterTurn = readRotationDegrees(context, uri) % 180 != 0
        return if (quarterTurn) opts.outHeight to opts.outWidth else opts.outWidth to opts.outHeight
    }

    /**
     * Decodes [uri] downsampled so its longest edge is at most [maxEdge], with
     * EXIF rotation already applied. Returns null if the image cannot be read.
     */
    fun decode(context: Context, uri: Uri, maxEdge: Int = EXPORT_MAX_EDGE): Bitmap? {
        // The elvis has to guard the stream, not the block. A bounds-only pass
        // returns a null Bitmap by definition, so `use { decodeStream(...) } ?:`
        // would abort on every image, however healthy.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
            // No alpha channel is needed for a printed page and it halves the
            // memory cost of every decode.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val pixelStream = context.contentResolver.openInputStream(uri) ?: return null
        val decoded = pixelStream.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = readRotationDegrees(context, uri)
        return if (rotation == 0) decoded else rotate(decoded, rotation)
    }

    /**
     * Largest power of two that keeps both edges within [maxEdge].
     * BitmapFactory rounds non-powers down anyway, so compute it explicitly.
     */
    internal fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= maxEdge || height / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        return sample
    }

    private fun readRotationDegrees(context: Context, uri: Uri): Int {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
