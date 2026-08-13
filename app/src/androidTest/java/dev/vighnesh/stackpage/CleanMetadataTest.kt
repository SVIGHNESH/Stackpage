package dev.vighnesh.stackpage

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vighnesh.stackpage.image.ImageSource
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The clean tool's mechanism is decode plus re-encode; this pins that the
 * mechanism actually strips what it promises. GPS and timestamps are written
 * onto a copy of a fixture with ExifInterface, then the same decode/encode
 * path the tool uses must produce bytes with neither.
 */
@RunWith(AndroidJUnit4::class)
class CleanMetadataTest {

    @Test
    fun decodeAndReencodeDropsGpsAndTimestamps() {
        val context = Fixtures.targetContext

        val tagged = File(context.cacheDir, "tagged.jpg")
        context.contentResolver.openInputStream(Fixtures.uriFor("plain-900x1200.jpg"))!!
            .use { input -> tagged.outputStream().use { input.copyTo(it) } }
        ExifInterface(tagged.absolutePath).apply {
            setLatLong(48.8583, 2.2945)
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:08:14 12:00:00")
            setAttribute(ExifInterface.TAG_MAKE, "TestCam")
            saveAttributes()
        }
        assertNotNull("fixture must carry GPS", ExifInterface(tagged.absolutePath).latLong)

        val bitmap = ImageSource.decode(context, android.net.Uri.fromFile(tagged), maxEdge = 4096)
        assertNotNull(bitmap)
        val cleaned = File(context.cacheDir, "cleaned.jpg")
        try {
            cleaned.outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap!!.recycle()
        }

        val exif = ExifInterface(cleaned.absolutePath)
        assertNull("GPS must be gone", exif.latLong)
        assertNull("timestamp must be gone", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertNull("camera make must be gone", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertTrue(cleaned.length() > 0)

        tagged.delete()
        cleaned.delete()
    }
}
