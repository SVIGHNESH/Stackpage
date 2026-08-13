package dev.vighnesh.stackpage

import dev.vighnesh.stackpage.pdf.CropRect
import dev.vighnesh.stackpage.pdf.cropPixels
import dev.vighnesh.stackpage.pdf.normalizeRotation
import dev.vighnesh.stackpage.pdf.transformedSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The transform contract: crop in source space first, then rotate. */
class PageSpecTest {

    @Test
    fun rotationNormalises() {
        assertEquals(0, normalizeRotation(0))
        assertEquals(90, normalizeRotation(90))
        assertEquals(270, normalizeRotation(-90))
        assertEquals(0, normalizeRotation(360))
        assertEquals(180, normalizeRotation(540))
        assertFailsWith<IllegalArgumentException> { normalizeRotation(45) }
    }

    @Test
    fun quarterTurnSwapsDimensions() {
        assertEquals(800 to 600, transformedSize(600, 800, 90, null))
        assertEquals(800 to 600, transformedSize(600, 800, 270, null))
        assertEquals(600 to 800, transformedSize(600, 800, 180, null))
        assertEquals(600 to 800, transformedSize(600, 800, 0, null))
    }

    @Test
    fun cropFractionsMapToPixels() {
        val p = cropPixels(1000, 500, CropRect(0.1f, 0.2f, 0.9f, 0.8f))
        assertEquals(100, p.x)
        assertEquals(100, p.y)
        assertEquals(800, p.width)
        assertEquals(300, p.height)
    }

    @Test
    fun cropAppliesBeforeRotation() {
        // A 1000x500 source cropped to its left half is 500x500; rotating a
        // square changes nothing. Rotating first then cropping would give a
        // 250x500 result, which is the bug this test exists to catch.
        assertEquals(500 to 500, transformedSize(1000, 500, 90, CropRect(0f, 0f, 0.5f, 1f)))
        // A non-square crop then a quarter turn swaps the cropped dims.
        assertEquals(300 to 800, transformedSize(1000, 500, 90, CropRect(0.1f, 0.2f, 0.9f, 0.8f)))
    }

    @Test
    fun cropNeverEscapesTheSource() {
        val p = cropPixels(3, 3, CropRect(0.9f, 0.9f, 1f, 1f))
        assertEquals(2, p.x)
        assertEquals(1, p.width)
        assertEquals(1, p.height)
    }

    @Test
    fun displayAndSourceCropsRoundTripAtEveryRotation() {
        val crop = CropRect(0.1f, 0.2f, 0.7f, 0.9f)
        for (rotation in listOf(0, 90, 180, 270)) {
            val there = dev.vighnesh.stackpage.pdf.displayToSourceCrop(crop, rotation)
            val back = dev.vighnesh.stackpage.pdf.sourceToDisplayCrop(there, rotation)
            // 1-(1-x) is not exact in float, so tolerance, not equality.
            for ((a, b) in listOf(
                crop.left to back.left, crop.top to back.top,
                crop.right to back.right, crop.bottom to back.bottom,
            )) {
                assertEquals(a.toDouble(), b.toDouble(), 1e-6, "round trip at $rotation")
            }
        }
    }

    @Test
    fun displayCropMapsToTheRightSourceRegion() {
        // Display shows the source rotated 90 clockwise. The top-left quarter
        // of the display is the source's bottom-left quarter.
        val displayTopLeft = CropRect(0f, 0f, 0.5f, 0.5f)
        val source = dev.vighnesh.stackpage.pdf.displayToSourceCrop(displayTopLeft, 90)
        assertEquals(CropRect(0f, 0.5f, 0.5f, 1f), source)
        // The full rect is rotation-invariant.
        val full = CropRect(0f, 0f, 1f, 1f)
        for (rotation in listOf(0, 90, 180, 270)) {
            assertEquals(full, dev.vighnesh.stackpage.pdf.displayToSourceCrop(full, rotation))
        }
    }

    @Test
    fun invalidCropsThrow() {
        assertFailsWith<IllegalArgumentException> { CropRect(0.5f, 0f, 0.5f, 1f) }
        assertFailsWith<IllegalArgumentException> { CropRect(-0.1f, 0f, 1f, 1f) }
        assertFailsWith<IllegalArgumentException> { CropRect(0f, 0.8f, 1f, 0.2f) }
        assertFailsWith<IllegalArgumentException> { cropPixels(0, 10, CropRect(0f, 0f, 1f, 1f)) }
    }
}
