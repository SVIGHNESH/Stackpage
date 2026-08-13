package dev.vighnesh.stackpage.feature.compress

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vighnesh.stackpage.image.Encoder
import dev.vighnesh.stackpage.image.ImageSource
import dev.vighnesh.stackpage.image.searchPlan
import dev.vighnesh.stackpage.io.MIME_JPEG
import dev.vighnesh.stackpage.io.compressedFileName
import dev.vighnesh.stackpage.io.createInTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One picked image and where its compression has got to. */
data class CompressItem(
    val uri: Uri,
    val displayName: String,
    val originalBytes: Long,
    val status: ItemStatus = ItemStatus.Pending,
)

sealed interface ItemStatus {
    data object Pending : ItemStatus
    data class Done(val bytes: Long, val fileName: String, val hitTarget: Boolean) : ItemStatus
    data class Failed(val message: String) : ItemStatus
}

data class CompressUiState(
    val items: List<CompressItem> = emptyList(),
    val targetBytes: Long = 200_000L,
    /** Probed result for the largest item, the honest "will be ~X" number. */
    val estimateBytes: Long? = null,
    val estimating: Boolean = false,
    val saving: Boolean = false,
    /** Non-null once a save-all pass finished; counts the successes. */
    val savedCount: Int? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

class CompressViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(CompressUiState())
    val state: StateFlow<CompressUiState> = _state.asStateFlow()

    private var estimateJob: Job? = null
    private var saveJob: Job? = null

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _state.value.items.map { it.uri }.toSet()
            val added = uris.filterNot { it in existing }.map { uri ->
                CompressItem(uri, displayNameOf(uri), sizeOf(uri))
            }
            _state.update { it.copy(items = it.items + added, savedCount = null) }
            reestimate()
        }
    }

    fun removeImage(uri: Uri) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.uri == uri }) }
        reestimate()
    }

    fun clearAll() {
        estimateJob?.cancel()
        _state.update { CompressUiState(targetBytes = it.targetBytes) }
    }

    fun setTarget(bytes: Long) {
        if (bytes <= 0) return
        _state.update { it.copy(targetBytes = bytes, savedCount = null) }
        reestimate()
    }

    /**
     * Probes the largest image only. It is the hardest to fit, so its result
     * bounds the batch, and one image keeps the estimate honest at the cost
     * of a single decode rather than N.
     */
    private fun reestimate() {
        estimateJob?.cancel()
        val current = _state.value
        val largest = current.items.maxByOrNull { it.originalBytes }
        if (largest == null) {
            _state.update { it.copy(estimateBytes = null, estimating = false) }
            return
        }
        _state.update { it.copy(estimating = true) }
        estimateJob = viewModelScope.launch(Dispatchers.IO) {
            val bitmap = ImageSource.decode(getApplication(), largest.uri)
            val estimate = bitmap?.let {
                try {
                    searchPlan(largest.originalBytes.coerceAtLeast(1), _state.value.targetBytes) { attempt ->
                        ensureActive()
                        Encoder.probe(it, attempt)
                    }.expectedBytes
                } finally {
                    it.recycle()
                }
            }
            _state.update { it.copy(estimateBytes = estimate, estimating = false) }
        }
    }

    /** Compresses every item into the SAF tree the user picked. */
    fun saveAll(treeUri: Uri) {
        if (_state.value.items.isEmpty() || _state.value.saving) return
        saveJob?.cancel()
        _state.update { it.copy(saving = true, savedCount = null) }

        saveJob = viewModelScope.launch(Dispatchers.IO) {
            val target = _state.value.targetBytes
            var saved = 0

            for (item in _state.value.items) {
                ensureActive()
                val status = runCatching { compressOne(item, target, treeUri) }
                    .getOrElse { e -> ItemStatus.Failed(e.message ?: "Could not compress.") }
                if (status is ItemStatus.Done) saved++
                _state.update { s ->
                    s.copy(items = s.items.map { if (it.uri == item.uri) it.copy(status = status) else it })
                }
            }
            _state.update { it.copy(saving = false, savedCount = saved) }
        }
    }

    private fun compressOne(item: CompressItem, targetBytes: Long, treeUri: Uri): ItemStatus {
        val app = getApplication<Application>()
        val bitmap = ImageSource.decode(app, item.uri)
            ?: return ItemStatus.Failed("Could not read the image.")
        val bytes: ByteArray
        val hit: Boolean
        try {
            val plan = searchPlan(item.originalBytes.coerceAtLeast(1), targetBytes) { attempt ->
                Encoder.probe(bitmap, attempt)
            }
            bytes = Encoder.encode(bitmap, plan.attempt)
            hit = plan.hitTarget
        } finally {
            bitmap.recycle()
        }

        val name = compressedFileName(item.displayName, targetBytes)
        val out = createInTree(app, treeUri, name, MIME_JPEG)
            ?: return ItemStatus.Failed("Could not create the file.")
        app.contentResolver.openOutputStream(out)?.use { it.write(bytes) }
            ?: return ItemStatus.Failed("Could not write the file.")
        return ItemStatus.Done(bytes.size.toLong(), name, hit)
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
}
