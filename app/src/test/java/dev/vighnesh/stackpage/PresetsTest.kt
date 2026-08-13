package dev.vighnesh.stackpage

import dev.vighnesh.stackpage.image.SIZE_PRESETS
import dev.vighnesh.stackpage.image.fitWithin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PresetsTest {

    @Test
    fun presetsAreUniqueAndPositive() {
        assertEquals(SIZE_PRESETS.size, SIZE_PRESETS.map { it.label }.toSet().size, "duplicate labels")
        assertEquals(
            SIZE_PRESETS.size,
            SIZE_PRESETS.map { it.width to it.height }.toSet().size,
            "duplicate dimensions",
        )
        SIZE_PRESETS.forEach {
            assertTrue(it.width > 0 && it.height > 0, "${it.label} has non-positive dimensions")
        }
    }

    @Test
    fun fitWithinKeepsAspectRatioAndFits() {
        val (w, h) = fitWithin(4000, 3000, 1080, 1080)
        assertTrue(w <= 1080 && h <= 1080)
        assertEquals(4.0 / 3.0, w.toDouble() / h, 0.01)
    }

    @Test
    fun fitWithinNeverUpscales() {
        assertEquals(100 to 60, fitWithin(100, 60, 1080, 1080))
    }

    @Test
    fun fitWithinHandlesTallSources() {
        val (w, h) = fitWithin(1000, 4000, 600, 600)
        assertTrue(w <= 600 && h <= 600)
        assertEquals(600, h)
    }

    @Test
    fun fitWithinRejectsBadInput() {
        assertFailsWith<IllegalArgumentException> { fitWithin(0, 10, 100, 100) }
        assertFailsWith<IllegalArgumentException> { fitWithin(10, 10, 0, 100) }
    }
}
