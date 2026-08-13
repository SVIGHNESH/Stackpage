package dev.vighnesh.stackpage.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri

/**
 * One page of the eventual PDF: the source image plus the edits applied to
 * it. The list order in the view model is the page order; this type is why
 * a page can now be more than its Uri.
 */
data class PageSpec(
    val uri: Uri,
    val rotationDegrees: Int = 0,
    val crop: CropRect? = null,
) {
    fun rotatedClockwise(): PageSpec = copy(rotationDegrees = normalizeRotation(rotationDegrees + 90))
}

/**
 * Applies crop then rotation to a decoded bitmap, recycling every
 * intermediate. The arithmetic lives in PageTransform.kt where the JVM can
 * reach it; this is only the Bitmap plumbing.
 */
fun Bitmap.applySpec(spec: PageSpec): Bitmap {
    var result = this

    spec.crop?.let { crop ->
        val p = cropPixels(result.width, result.height, crop)
        val cropped = Bitmap.createBitmap(result, p.x, p.y, p.width, p.height)
        if (cropped !== result) result.recycle()
        result = cropped
    }

    val rotation = normalizeRotation(spec.rotationDegrees)
    if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
        if (rotated !== result) result.recycle()
        result = rotated
    }

    return result
}
