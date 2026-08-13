package dev.vighnesh.stackpage.feature.stack

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vighnesh.stackpage.pdf.ExportOptions
import dev.vighnesh.stackpage.pdf.ExportResult
import dev.vighnesh.stackpage.pdf.Margin
import dev.vighnesh.stackpage.pdf.PageOrientation
import dev.vighnesh.stackpage.pdf.PageSize
import dev.vighnesh.stackpage.pdf.CropRect
import dev.vighnesh.stackpage.pdf.PageSpec
import dev.vighnesh.stackpage.pdf.PdfExporter
import dev.vighnesh.stackpage.pdf.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the export has got to. Drives which surface the UI shows. */
sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val done: Int, val total: Int) : ExportState
    data class Done(val target: Uri, val pageCount: Int, val byteSize: Long) : ExportState
    data class Failed(val message: String) : ExportState
}

data class UiState(
    val pages: List<PageSpec> = emptyList(),
    val options: ExportOptions = ExportOptions(),
    val export: ExportState = ExportState.Idle,
    /** Non-null while a PDF import renders pages: done to total. */
    val importing: Pair<Int, Int>? = null,
    /** One-line message the UI surfaces once, e.g. the rasterisation note. */
    val notice: String? = null,
    /** The page open in the crop/rotate editor, if any. */
    val editing: PageSpec? = null,
) {
    val isEmpty: Boolean get() = pages.isEmpty()
}

class StackViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var exportJob: Job? = null
    private var importJob: Job? = null

    init {
        // Rendered pages from previous sessions are orphans: the page list
        // does not survive process death, so the files must not either.
        viewModelScope.launch(Dispatchers.IO) { PdfImporter.clearCache(getApplication()) }
    }

    /** Appends a pick, skipping anything already in the stack. */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { s ->
            val existing = s.pages.map { it.uri }.toSet()
            s.copy(pages = s.pages + uris.filterNot { it in existing }.map { PageSpec(it) })
        }
    }

    fun removeImage(uri: Uri) {
        _state.update { s -> s.copy(pages = s.pages.filterNot { it.uri == uri }) }
    }

    /** Quarter clockwise turn each tap; the export applies it for real. */
    fun rotatePage(uri: Uri) {
        _state.update { s ->
            s.copy(pages = s.pages.map { if (it.uri == uri) it.rotatedClockwise() else it })
        }
    }

    fun openEditor(uri: Uri) {
        _state.update { s -> s.copy(editing = s.pages.firstOrNull { it.uri == uri }) }
    }

    fun closeEditor() {
        _state.update { it.copy(editing = null) }
    }

    fun applyEdit(uri: Uri, rotationDegrees: Int, crop: CropRect?) {
        _state.update { s ->
            s.copy(
                pages = s.pages.map {
                    if (it.uri == uri) it.copy(rotationDegrees = rotationDegrees, crop = crop) else it
                },
                editing = null,
            )
        }
    }

    fun clearAll() {
        _state.update { it.copy(pages = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) { PdfImporter.clearCache(getApplication()) }
    }

    /** Renders an existing PDF's pages into the stack as images. */
    fun importPdf(uri: Uri) {
        if (_state.value.importing != null) return
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(importing = 0 to 0) }
            val result = PdfImporter.importPages(
                context = getApplication(),
                uri = uri,
                onProgress = { done, total -> _state.update { it.copy(importing = done to total) } },
                isActive = { isActive },
            )
            _state.update { s ->
                when (result) {
                    is PdfImporter.ImportResult.Pages -> s.copy(
                        pages = s.pages + result.uris.map { PageSpec(it) },
                        importing = null,
                        notice = "PDF pages are re-saved as images in the export.",
                    )
                    is PdfImporter.ImportResult.Failed -> s.copy(
                        importing = null,
                        notice = result.message,
                    )
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _state.update { it.copy(importing = null) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** Moves the page at [from] to index [to], keeping every other page's order. */
    fun move(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.pages.indices || to !in s.pages.indices || from == to) return@update s
            val next = s.pages.toMutableList()
            next.add(to, next.removeAt(from))
            s.copy(pages = next)
        }
    }

    fun setPageSize(size: PageSize) =
        _state.update { it.copy(options = it.options.copy(pageSize = size)) }

    fun setOrientation(orientation: PageOrientation) =
        _state.update { it.copy(options = it.options.copy(orientation = orientation)) }

    fun setMargin(margin: Margin) =
        _state.update { it.copy(options = it.options.copy(margin = margin)) }

    /** Default filename offered to the system file picker. */
    fun suggestedFileName(): String {
        val count = _state.value.pages.size
        return if (count == 1) "Stackpage.pdf" else "Stackpage-$count-pages.pdf"
    }

    fun export(target: Uri) {
        val current = _state.value
        if (current.pages.isEmpty()) return

        exportJob?.cancel()
        _state.update { it.copy(export = ExportState.Running(0, current.pages.size)) }

        exportJob = viewModelScope.launch {
            val result = PdfExporter.export(
                context = getApplication(),
                pages = current.pages,
                target = target,
                options = current.options,
                onProgress = { done, total ->
                    _state.update { it.copy(export = ExportState.Running(done, total)) }
                },
            )
            _state.update {
                it.copy(
                    export = when (result) {
                        is ExportResult.Success ->
                            ExportState.Done(result.target, result.pageCount, result.byteSize)
                        is ExportResult.Failure -> ExportState.Failed(result.message)
                    },
                )
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _state.update { it.copy(export = ExportState.Idle) }
    }

    fun dismissExportResult() {
        _state.update { it.copy(export = ExportState.Idle) }
    }
}
