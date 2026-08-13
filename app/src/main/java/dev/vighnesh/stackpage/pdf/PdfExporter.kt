package dev.vighnesh.stackpage.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dev.vighnesh.stackpage.image.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Everything the user chose in the export sheet. */
data class ExportOptions(
    val pageSize: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.AUTO,
    val margin: Margin = Margin.SMALL,
)

sealed interface ExportResult {
    data class Success(val target: Uri, val pageCount: Int, val byteSize: Long) : ExportResult
    data class Failure(val message: String) : ExportResult
}

/**
 * Writes images to a PDF using the platform's own [PdfDocument].
 *
 * The platform writer is deliberate: it has no licence encumbrance, ships with
 * the OS, and needs no third-party parser. It also means the whole export path
 * runs offline - nothing here opens a socket.
 */
object PdfExporter {

    /**
     * @param onProgress called with the number of pages written so far.
     */
    suspend fun export(
        context: Context,
        pages: List<PageSpec>,
        target: Uri,
        options: ExportOptions,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportResult = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext ExportResult.Failure("No images selected.")

        val document = PdfDocument()
        // Filtering matters: pages are downscales of the source, and without it
        // the result looks visibly aliased next to any other PDF tool's output.
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var written = 0

        try {
            pages.forEachIndexed { index, spec ->
                coroutineContext.ensureActive()

                val decoded = ImageSource.decode(context, spec.uri)
                    ?: return@withContext ExportResult.Failure(
                        "Could not read image ${index + 1} of ${pages.size}.",
                    )
                // Crop and rotation happen between decode and layout, so the
                // page geometry only ever sees the final image shape.
                val bitmap = decoded.applySpec(spec)

                try {
                    val layout = layoutPage(
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        pageSize = options.pageSize,
                        orientation = options.orientation,
                        margin = options.margin,
                    )

                    // PdfDocument page dimensions are integer points; rounding up
                    // avoids shaving a fraction of a point off the content box.
                    val info = PdfDocument.PageInfo.Builder(
                        Math.round(layout.pageWidthPt),
                        Math.round(layout.pageHeightPt),
                        index + 1,
                    ).create()

                    val page = document.startPage(info)
                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(
                            layout.dest.left,
                            layout.dest.top,
                            layout.dest.right,
                            layout.dest.bottom,
                        ),
                        paint,
                    )
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }

                written = index + 1
                onProgress(written, pages.size)
            }

            context.contentResolver.openOutputStream(target)?.use { out ->
                document.writeTo(out)
            } ?: return@withContext ExportResult.Failure("Could not open the destination file.")

            val size = context.contentResolver.openFileDescriptor(target, "r")?.use {
                it.statSize.coerceAtLeast(0L)
            } ?: 0L

            ExportResult.Success(target, written, size)
        } catch (e: OutOfMemoryError) {
            ExportResult.Failure("Ran out of memory at page ${written + 1}. Try fewer images.")
        } catch (e: Exception) {
            ExportResult.Failure(e.message ?: "Export failed.")
        } finally {
            document.close()
        }
    }
}
