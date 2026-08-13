package dev.vighnesh.stackpage

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vighnesh.stackpage.image.ImageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decode regressions were invisible to the JVM suite; these run the real
 * BitmapFactory + ExifInterface path on hardware against bundled fixtures.
 */
@RunWith(AndroidJUnit4::class)
class ImageSourceTest {

    private val context get() = Fixtures.targetContext

    @Test
    fun plainImageDecodesAtFullSize() {
        val bitmap = ImageSource.decode(context, Fixtures.uriFor("plain-900x1200.jpg"))
        assertNotNull(bitmap)
        assertEquals(900, bitmap!!.width)
        assertEquals(1200, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun exifRotationIsAppliedOnDecode() {
        // Stored 900x1200 with EXIF orientation 6; upright means 1200x900.
        val bitmap = ImageSource.decode(context, Fixtures.uriFor("rotated-90.jpg"))
        assertNotNull(bitmap)
        assertEquals(1200, bitmap!!.width)
        assertEquals(900, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun hugeImageIsDownsampled() {
        val bitmap = ImageSource.decode(context, Fixtures.uriFor("huge-6000x8000.jpg"))
        assertNotNull(bitmap)
        val longEdge = maxOf(bitmap!!.width, bitmap.height)
        // Power-of-two sampling guarantees the long edge lands within
        // [EXPORT_MAX_EDGE, 2 * EXPORT_MAX_EDGE); the exact bound is enforced
        // later by the page layout's dest rect, not by decode.
        assertTrue(
            "long edge $longEdge should be under 2x EXPORT_MAX_EDGE",
            longEdge < ImageSource.EXPORT_MAX_EDGE * 2,
        )
        // 6000x8000 at inSampleSize 2 -> 3000x4000.
        assertEquals(3000, bitmap.width)
        assertEquals(4000, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun garbageBytesReturnNullInsteadOfThrowing() {
        assertNull(ImageSource.decode(context, Fixtures.uriFor("not-an-image.bin")))
        assertNull(ImageSource.readSize(context, Fixtures.uriFor("not-an-image.bin")))
    }

    @Test
    fun readSizeAgreesWithDecodeOnOrientation() {
        for (asset in listOf("plain-900x1200.jpg", "rotated-90.jpg")) {
            val uri = Fixtures.uriFor(asset)
            val size = ImageSource.readSize(context, uri)
            assertNotNull("readSize failed for $asset", size)
            val bitmap = ImageSource.decode(context, uri)!!
            assertEquals("width for $asset", bitmap.width, size!!.first)
            assertEquals("height for $asset", bitmap.height, size.second)
            bitmap.recycle()
        }
    }
}
