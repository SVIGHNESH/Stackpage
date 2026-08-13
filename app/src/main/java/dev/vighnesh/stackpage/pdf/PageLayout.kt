package dev.vighnesh.stackpage.pdf

/**
 * Page geometry for PDF export, in PostScript points (1 pt = 1/72 inch).
 *
 * This file is deliberately free of Android types so the arithmetic that
 * decides where an image lands on a page can be unit-tested on the JVM. Getting
 * this wrong - stretched images, wrong aspect ratio, margins eaten by the
 * bleed - is the single most common complaint about apps in this category, so
 * it is the part that gets tests.
 */

/** A4 and Letter are the two sizes anyone actually asks for. */
enum class PageSize(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595f, 842f),
    LETTER("Letter", 612f, 792f),
    LEGAL("Legal", 612f, 1008f),

    /**
     * The page takes the shape of the image itself, so nothing is ever letterboxed.
     * Width and height are unused and resolved per-image in [layoutPage].
     */
    FIT_IMAGE("Fit to image", 0f, 0f),
}

enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE,

    /** Pick per image: a landscape photo gets a landscape page. */
    AUTO,
}

/** Margin presets, expressed in points so they survive any DPI. */
enum class Margin(val label: String, val points: Float) {
    NONE("None", 0f),
    SMALL("Small", 18f), // 0.25"
    MEDIUM("Medium", 36f), // 0.5"
    LARGE("Large", 72f), // 1"
}

/** Destination rectangle in page coordinates, origin top-left. */
data class DestRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A fully resolved page: how big it is, and where the image sits on it. */
data class PageLayout(
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val dest: DestRect,
)

/**
 * Resolves one image onto one page.
 *
 * The image is scaled to fit inside the content box (page minus margins) with
 * its aspect ratio preserved, then centred. It is never upscaled beyond the
 * content box and never cropped.
 *
 * @param imageWidth pixel width of the image *after* EXIF rotation is applied.
 * @param imageHeight pixel height of the image after EXIF rotation.
 */
fun layoutPage(
    imageWidth: Int,
    imageHeight: Int,
    pageSize: PageSize,
    orientation: PageOrientation,
    margin: Margin,
): PageLayout {
    require(imageWidth > 0 && imageHeight > 0) {
        "image must have positive dimensions, got ${imageWidth}x$imageHeight"
    }

    val marginPt = margin.points

    if (pageSize == PageSize.FIT_IMAGE) {
        // The page grows to hold the image at 1 px = 1 pt, plus the margin on
        // every side. Orientation is meaningless here: the image already has one.
        return PageLayout(
            pageWidthPt = imageWidth + 2 * marginPt,
            pageHeightPt = imageHeight + 2 * marginPt,
            dest = DestRect(marginPt, marginPt, marginPt + imageWidth, marginPt + imageHeight),
        )
    }

    val landscape = when (orientation) {
        PageOrientation.PORTRAIT -> false
        PageOrientation.LANDSCAPE -> true
        PageOrientation.AUTO -> imageWidth > imageHeight
    }
    val pageW = if (landscape) pageSize.heightPt else pageSize.widthPt
    val pageH = if (landscape) pageSize.widthPt else pageSize.heightPt

    // A margin wide enough to close the content box would produce a zero or
    // negative destination rect, so clamp it to just under half the short side.
    val maxMargin = (minOf(pageW, pageH) / 2f) - 1f
    val m = marginPt.coerceAtMost(maxMargin)

    val boxW = pageW - 2 * m
    val boxH = pageH - 2 * m
    val scale = minOf(boxW / imageWidth, boxH / imageHeight)

    val drawW = imageWidth * scale
    val drawH = imageHeight * scale
    val left = m + (boxW - drawW) / 2f
    val top = m + (boxH - drawH) / 2f

    return PageLayout(
        pageWidthPt = pageW,
        pageHeightPt = pageH,
        dest = DestRect(left, top, left + drawW, top + drawH),
    )
}
