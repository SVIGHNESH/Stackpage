package dev.vighnesh.stackpage

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vighnesh.stackpage.pdf.ExportOptions
import dev.vighnesh.stackpage.pdf.ExportResult
import dev.vighnesh.stackpage.pdf.PdfExporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {

    @Test
    fun exportsTwoImagesToATwoPagePdf() = runBlocking {
        val context = Fixtures.targetContext
        val images = listOf(
            Fixtures.uriFor("plain-900x1200.jpg"),
            Fixtures.uriFor("rotated-90.jpg"),
        )
        val out = File(context.cacheDir, "smoke.pdf")
        out.delete()

        val result = PdfExporter.export(context, images, Uri.fromFile(out), ExportOptions())

        assertTrue("export failed: $result", result is ExportResult.Success)
        assertEquals(2, (result as ExportResult.Success).pageCount)

        val bytes = out.readBytes()
        assertTrue("file should start with %PDF", bytes.size > 4 && bytes.decodeToString(0, 4) == "%PDF")
        val text = bytes.decodeToString()
        val pages = Regex("/Type\\s*/Page[^s]").findAll(text).count()
        assertEquals("two /Type /Page entries", 2, pages)
        out.delete()
    }
}
