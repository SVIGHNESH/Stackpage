package dev.vighnesh.stackpage

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vighnesh.stackpage.image.ImageSource
import dev.vighnesh.stackpage.pdf.ExportOptions
import dev.vighnesh.stackpage.pdf.ExportResult
import dev.vighnesh.stackpage.pdf.PageSpec
import dev.vighnesh.stackpage.pdf.PdfExporter
import dev.vighnesh.stackpage.pdf.PdfImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Round-trips through our own exporter so no binary PDF fixture is needed:
 * export two fixture images, import the result, and expect two decodable
 * page images back.
 */
@RunWith(AndroidJUnit4::class)
class PdfImporterTest {

    @Test
    fun importedPdfYieldsOnePageImagePerPage(): Unit = runBlocking {
        val context = Fixtures.targetContext
        val pdf = File(context.cacheDir, "roundtrip.pdf")
        pdf.delete()

        val exported = PdfExporter.export(
            context,
            listOf(
                PageSpec(Fixtures.uriFor("plain-900x1200.jpg")),
                PageSpec(Fixtures.uriFor("rotated-90.jpg")),
            ),
            Uri.fromFile(pdf),
            ExportOptions(),
        )
        assertTrue("export failed: $exported", exported is ExportResult.Success)

        val result = PdfImporter.importPages(context, Uri.fromFile(pdf))
        assertTrue("import failed: $result", result is PdfImporter.ImportResult.Pages)
        val pages = (result as PdfImporter.ImportResult.Pages).uris
        assertEquals(2, pages.size)

        pages.forEach { pageUri ->
            val bitmap = ImageSource.decode(context, pageUri)
            assertNotNull("page image should decode: $pageUri", bitmap)
            bitmap!!.recycle()
        }

        PdfImporter.clearCache(context)
        pdf.delete()
    }

    @Test
    fun garbageBytesFailGracefully() {
        val result = PdfImporter.importPages(
            Fixtures.targetContext,
            Fixtures.uriFor("not-an-image.bin"),
        )
        assertTrue(result is PdfImporter.ImportResult.Failed)
    }
}
