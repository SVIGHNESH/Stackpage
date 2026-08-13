package dev.vighnesh.stackpage.pdf

import kotlin.math.roundToInt

/**
 * Per-page transform arithmetic: crop first, then rotate.
 *
 * Pure Kotlin like PageLayout, and for the same reason: the off-by-one and
 * axis-swap bugs live in this maths, so it must be testable without a device.
 * The crop is stored as fractions of the source so it survives any decode
 * downsampling - the same rect means the same thing at every resolution.
 */

/** A crop in source-relative fractions, 0..1 on both axes. */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) {
            "crop fractions must be within 0..1"
        }
        require(left < right && top < bottom) { "crop must have positive area" }
    }
}

/** A pixel-space rect the platform crop call can consume directly. */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** Normalises any rotation to one of 0, 90, 180, 270. */
fun normalizeRotation(degrees: Int): Int {
    require(degrees % 90 == 0) { "rotation must be a multiple of 90, was $degrees" }
    return ((degrees % 360) + 360) % 360
}

/**
 * The crop in pixels for a source of the given decoded size. Edges are
 * rounded independently rather than truncating a float span, so a 0.1..0.9
 * crop of 1000px is exactly 800px and not a float-error 799.
 */
fun cropPixels(width: Int, height: Int, crop: CropRect): PixelRect {
    require(width > 0 && height > 0) { "source dimensions must be positive" }
    val x = (crop.left * width).roundToInt().coerceIn(0, width - 1)
    val x2 = (crop.right * width).roundToInt().coerceIn(x + 1, width)
    val y = (crop.top * height).roundToInt().coerceIn(0, height - 1)
    val y2 = (crop.bottom * height).roundToInt().coerceIn(y + 1, height)
    return PixelRect(x, y, x2 - x, y2 - y)
}

/**
 * Maps a crop drawn on the *rotated, displayed* image back to source-space
 * fractions, which is what PageSpec stores because the export crops before
 * it rotates. The editor shows the image the way the page will look, so the
 * user drags in display space; this is the change of coordinates.
 *
 * Derivation for 90 (clockwise): a source fraction point (x, y) lands at
 * display (1-y, x), so the inverse is x = v, y = 1-u.
 */
fun displayToSourceCrop(crop: CropRect, rotationDegrees: Int): CropRect =
    when (normalizeRotation(rotationDegrees)) {
        0 -> crop
        90 -> CropRect(crop.top, 1f - crop.right, crop.bottom, 1f - crop.left)
        180 -> CropRect(1f - crop.right, 1f - crop.bottom, 1f - crop.left, 1f - crop.top)
        else -> CropRect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right)
    }

/** The inverse of [displayToSourceCrop]: where a stored crop sits on screen. */
fun sourceToDisplayCrop(crop: CropRect, rotationDegrees: Int): CropRect =
    when (normalizeRotation(rotationDegrees)) {
        0 -> crop
        90 -> CropRect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right)
        180 -> CropRect(1f - crop.right, 1f - crop.bottom, 1f - crop.left, 1f - crop.top)
        else -> CropRect(crop.top, 1f - crop.right, crop.bottom, 1f - crop.left)
    }

/** Page dimensions after crop and rotation, the size layoutPage will see. */
fun transformedSize(width: Int, height: Int, rotationDegrees: Int, crop: CropRect?): Pair<Int, Int> {
    require(width > 0 && height > 0) { "source dimensions must be positive" }
    val (w, h) = if (crop != null) {
        val p = cropPixels(width, height, crop)
        p.width to p.height
    } else {
        width to height
    }
    return if (normalizeRotation(rotationDegrees) % 180 == 0) w to h else h to w
}
