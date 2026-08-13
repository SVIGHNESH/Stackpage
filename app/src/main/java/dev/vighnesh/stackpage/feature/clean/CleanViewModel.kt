package dev.vighnesh.stackpage.feature.clean

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vighnesh.stackpage.image.ImageSource
import dev.vighnesh.stackpage.io.MIME_JPEG
import dev.vighnesh.stackpage.io.createInTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Metadata removal by reconstruction, not by editing tags: decode the pixels
 * (EXIF rotation applied so the output is upright), re-encode a fresh JPEG,
 * and everything that was not pixels - GPS position, timestamps, camera
 * serials, thumbnails - simply does not exist in the copy.
 */

data class CleanItem(
    val uri: Uri,
    val displayName: String,
    val originalBytes: Long,
    val status: CleanStatus = CleanStatus.Pending,
)

sealed interface CleanStatus {
    data object Pending : CleanStatus
    data class Done(val bytes: Long, val fileName: String) : CleanStatus
    data class Failed(val message: String) : CleanStatus
}

data class CleanUiState(
    val items: List<CleanItem> = emptyList(),
    val saving: Boolean = false,
    val savedCount: Int? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

class CleanViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(CleanUiState())
    val state: StateFlow<CleanUiState> = _state.asStateFlow()

    private var saveJob: Job? = null

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _state.value.items.map { it.uri }.toSet()
            val added = uris.filterNot { it in existing }.map { uri ->
                CleanItem(uri, displayNameOf(uri), sizeOf(uri))
            }
            _state.update { it.copy(items = it.items + added, savedCount = null) }
        }
    }

    fun removeImage(uri: Uri) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.uri == uri }) }
    }

    fun clearAll() {
        _state.update { CleanUiState() }
    }

    fun saveAll(treeUri: Uri) {
        if (_state.value.items.isEmpty() || _state.value.saving) return
        saveJob?.cancel()
        _state.update { it.copy(saving = true, savedCount = null) }

        saveJob = viewModelScope.launch(Dispatchers.IO) {
            var saved = 0
            for (item in _state.value.items) {
                ensureActive()
                val status = runCatching { cleanOne(item, treeUri) }
                    .getOrElse { e -> CleanStatus.Failed(e.message ?: "Could not clean.") }
                if (status is CleanStatus.Done) saved++
                _state.update { s ->
                    s.copy(items = s.items.map { if (it.uri == item.uri) it.copy(status = status) else it })
                }
            }
            _state.update { it.copy(saving = false, savedCount = saved) }
        }
    }

    private fun cleanOne(item: CleanItem, treeUri: Uri): CleanStatus {
        val app = getApplication<Application>()
        val bitmap = ImageSource.decode(app, item.uri, maxEdge = CLEAN_MAX_EDGE)
            ?: return CleanStatus.Failed("Could not read the image.")

        val bytes: ByteArray
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, ENCODE_QUALITY, out)
            bytes = out.toByteArray()
        } finally {
            bitmap.recycle()
        }

        val base = item.displayName.substringBeforeLast('.').ifBlank { "image" }
        val name = "$base-clean.jpg"
        val out = createInTree(app, treeUri, name, MIME_JPEG)
            ?: return CleanStatus.Failed("Could not create the file.")
        app.contentResolver.openOutputStream(out)?.use { it.write(bytes) }
            ?: return CleanStatus.Failed("Could not write the file.")
        return CleanStatus.Done(bytes.size.toLong(), name)
    }

    fun dismissSaveResult() {
        _state.update { it.copy(savedCount = null) }
    }

    private fun displayNameOf(uri: Uri): String {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst() && !it.isNull(0)) return it.getString(0) }
        return "image"
    }

    private fun sizeOf(uri: Uri): Long {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0) }
        return getApplication<Application>().contentResolver
            .openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0) } ?: 0L
    }

    companion object {
        /**
         * A deliberate ceiling, not an accident: cleaning must not silently
         * shrink photos, but an unbounded decode of a 50MP source is an OOM.
         * 4096px RGB_565 is ~25MB per decode and past what portals or
         * sharing need; the UI states the cap in words.
         */
        const val CLEAN_MAX_EDGE = 4096
        const val ENCODE_QUALITY = 95
    }
}
