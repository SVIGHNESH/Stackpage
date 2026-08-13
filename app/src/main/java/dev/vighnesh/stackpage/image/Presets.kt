package dev.vighnesh.stackpage.image

/**
 * Resize presets in pixels. Pure Kotlin so the table and the fit arithmetic
 * are JVM-tested. Document sizes assume 300dpi, which is what portals and
 * print shops expect; social sizes are the platforms' own canvas sizes.
 */
data class SizePreset(val label: String, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "$label has non-positive dimensions" }
    }
}

val SIZE_PRESETS: List<SizePreset> = listOf(
    SizePreset("Passport 35x45 mm", 413, 531),
    SizePreset("Visa 2x2 in", 600, 600),
    SizePreset("Signature 140x60", 140, 60),
    SizePreset("Square 1080", 1080, 1080),
    SizePreset("Story 1080x1920", 1080, 1920),
    SizePreset("Full HD 1920x1080", 1920, 1080),
)

/**
 * Scales (w, h) to fit inside (maxW, maxH) keeping aspect ratio.
 * Never upscales: an image already inside the box keeps its size, because
 * inventing pixels helps no portal and bloats every file.
 */
fun fitWithin(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    require(width > 0 && height > 0) { "source dimensions must be positive" }
    require(maxWidth > 0 && maxHeight > 0) { "bounds must be positive" }
    if (width <= maxWidth && height <= maxHeight) return width to height
    val scale = minOf(maxWidth.toDouble() / width, maxHeight.toDouble() / height)
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}
