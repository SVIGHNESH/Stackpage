package dev.vighnesh.stackpage.feature.sign

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Stamping a drawn signature onto a page of an existing PDF.
 *
 * The stamp is the only raster added: PDFBox appends one image draw to the
 * chosen page's content stream, so a born-digital PDF keeps its text. The
 * placement is stored as fractions of the displayed page and converted to
 * PDF points (origin bottom-left) only at save time.
 */

/** Where the signature sits on the page, all as fractions of page size. */
data class Placement(
    val leftFrac: Float = 0.55f,
    val topFrac: Float = 0.75f,
    val widthFrac: Float = 0.3f,
)

sealed interface SignStatus {
    data object Idle : SignStatus
    data object Working : SignStatus
    data class Done(val byteSize: Long) : SignStatus
    data class Failed(val message: String) : SignStatus
}

data class SignUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val pageCount: Int = 0,
    val pageIndex: Int = 0,
    val pagePreview: Bitmap? = null,
    val signature: Bitmap? = null,
    val drawing: Boolean = false,
    val placement: Placement = Placement(),
    val status: SignStatus = SignStatus.Idle,
)

class SignViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SignUiState())
    val state: StateFlow<SignUiState> = _state.asStateFlow()

    /** Local copy of the source; PdfRenderer needs a seekable descriptor. */
    private var sourceCopy: File? = null

    init {
        _state.update { it.copy(signature = SignatureStore.load(app)) }
    }

    fun setSource(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val copy = File(app.cacheDir, "sign-source-${System.nanoTime()}.pdf")
            val ok = runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    copy.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (!ok) {
                _state.update { it.copy(status = SignStatus.Failed("Could not read the PDF.")) }
                return@launch
            }
            sourceCopy?.delete()
            sourceCopy = copy

            val count = runCatching {
                ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                }
            }.getOrDefault(0)
            if (count == 0) {
                _state.update {
                    it.copy(status = SignStatus.Failed("Could not open the PDF. Is it password-protected?"))
                }
                return@launch
            }
            _state.update {
                it.copy(
                    source = uri,
                    sourceName = displayNameOf(uri),
                    pageCount = count,
                    pageIndex = 0,
                    status = SignStatus.Idle,
                )
            }
            renderPage(0)
        }
    }

    fun setPage(index: Int) {
        val clamped = index.coerceIn(0, (_state.value.pageCount - 1).coerceAtLeast(0))
        _state.update { it.copy(pageIndex = clamped) }
        viewModelScope.launch(Dispatchers.IO) { renderPage(clamped) }
    }

    private fun renderPage(index: Int) {
        val copy = sourceCopy ?: return
        val bitmap = runCatching {
            ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.openPage(index).use { page ->
                        val scale = (PREVIEW_MAX_EDGE.toFloat() / maxOf(page.width, page.height))
                            .coerceAtMost(4f)
                        val b = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        b.eraseColor(Color.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    }
                }
            }
        }.getOrNull()
        _state.update { it.copy(pagePreview = bitmap) }
    }

    fun startDrawing() {
        _state.update { it.copy(drawing = true) }
    }

    fun cancelDrawing() {
        _state.update { it.copy(drawing = false) }
    }

    fun saveSignature(bitmap: Bitmap) {
        SignatureStore.save(getApplication(), bitmap)
        _state.update { it.copy(signature = bitmap, drawing = false) }
    }

    fun setPlacement(placement: Placement) {
        _state.update { it.copy(placement = placement) }
    }

    fun suggestedFileName(): String {
        val base = _state.value.sourceName.substringBeforeLast('.').ifBlank { "document" }
        return "$base-signed.pdf"
    }

    fun signTo(target: Uri) {
        val current = _state.value
        val copy = sourceCopy
        val signatureBytes = SignatureStore.loadBytes(getApplication())
        if (copy == null || signatureBytes == null || current.signature == null) return

        _state.update { it.copy(status = SignStatus.Working) }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val status = runCatching {
                PDFBoxResourceLoader.init(app)
                PDDocument.load(copy).use { doc ->
                    val page = doc.getPage(current.pageIndex)
                    val box = page.mediaBox
                    val sig = current.signature

                    val widthPt = current.placement.widthFrac * box.width
                    val heightPt = widthPt * sig.height / sig.width
                    val xPt = current.placement.leftFrac * box.width
                    // Display top fraction to PDF bottom-left origin.
                    val yPt = box.height - (current.placement.topFrac * box.height) - heightPt

                    val image = PDImageXObject.createFromByteArray(doc, signatureBytes, "signature")
                    PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true,
                    ).use { stream ->
                        stream.drawImage(image, xPt, yPt, widthPt, heightPt)
                    }

                    app.contentResolver.openOutputStream(target)?.use { doc.save(it) }
                        ?: return@runCatching SignStatus.Failed("Could not write the file.")
                }
                val size = app.contentResolver.openFileDescriptor(target, "r")
                    ?.use { it.statSize.coerceAtLeast(0) } ?: 0L
                SignStatus.Done(size)
            }.getOrElse { e -> SignStatus.Failed(e.message ?: "Could not sign the PDF.") }
            _state.update { it.copy(status = status) }
        }
    }

    fun dismissResult() {
        _state.update { it.copy(status = SignStatus.Idle) }
    }

    fun clear() {
        sourceCopy?.delete()
        sourceCopy = null
        _state.update {
            SignUiState(signature = it.signature)
        }
    }

    override fun onCleared() {
        sourceCopy?.delete()
    }

    private fun displayNameOf(uri: Uri): String {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst() && !it.isNull(0)) return it.getString(0) }
        return "document.pdf"
    }

    private companion object {
        const val PREVIEW_MAX_EDGE = 1600
    }
}
