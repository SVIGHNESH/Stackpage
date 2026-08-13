package dev.vighnesh.stackpage.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Renders an existing PDF's pages to JPEG files in the cache so they can
 * join the stack grid like any picked image.
 *
 * This is rasterisation, stated plainly: a born-digital PDF's text becomes
 * pixels. The UI owns saying that to the user; this object owns doing it
 * safely. Imported page files live under [importCacheDir] and are cleaned
 * when the stack is cleared and on process start, because "no cache holding
 * the user's documents" is part of the product's privacy posture.
 */
object PdfImporter {

    /** Render resolution. 150dpi keeps an A4 page near 1240x1754. */
    private const val RENDER_DPI = 150

    /** A page never renders past the same bound exports decode at. */
    private const val MAX_EDGE = 2400

    sealed interface ImportResult {
        data class Pages(val uris: List<Uri>) : ImportResult
        data class Failed(val message: String) : ImportResult
    }

    fun importCacheDir(context: Context): File = File(context.cacheDir, "imported-pdf-pages")

    /** Deletes every previously rendered page file. */
    fun clearCache(context: Context) {
        importCacheDir(context).listFiles()?.forEach { it.delete() }
    }

    fun importPages(
        context: Context,
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        isActive: () -> Boolean = { true },
    ): ImportResult {
        // PdfRenderer needs a seekable descriptor; a copy in our own cache is
        // the robust route for any SAF provider, and the pages land in cache
        // anyway.
        val copy = File(importCacheDir(context).apply { mkdirs() }, "source-${System.nanoTime()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it) }
            } ?: return ImportResult.Failed("Could not read the PDF.")

            val uris = mutableListOf<Uri>()
            ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    if (total == 0) return ImportResult.Failed("The PDF has no pages.")

                    for (index in 0 until total) {
                        if (!isActive()) return ImportResult.Failed("Cancelled.")
                        renderer.openPage(index).use { page ->
                            val scale = (RENDER_DPI / 72f)
                                .coerceAtMost(MAX_EDGE.toFloat() / maxOf(page.width, page.height))
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)

                            // render() requires ARGB_8888 and draws over the
                            // existing pixels, so paint the page white first
                            // or transparent regions come out black in JPEG.
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val out = File(
                                    importCacheDir(context),
                                    "page-${System.nanoTime()}-${index + 1}.jpg",
                                )
                                out.outputStream().use { stream ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                                }
                                uris += Uri.fromFile(out)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        onProgress(index + 1, total)
                    }
                }
            }
            return ImportResult.Pages(uris)
        } catch (e: SecurityException) {
            return ImportResult.Failed("This PDF is password-protected.")
        } catch (e: Exception) {
            return ImportResult.Failed(e.message ?: "Could not open the PDF.")
        } finally {
            copy.delete()
        }
    }
}
