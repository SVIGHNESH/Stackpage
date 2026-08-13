package dev.vighnesh.stackpage.feature.convert

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vighnesh.stackpage.image.ImageSource
import dev.vighnesh.stackpage.image.SizePreset
import dev.vighnesh.stackpage.image.fitWithin
import dev.vighnesh.stackpage.io.MIME_JPEG
import dev.vighnesh.stackpage.io.MIME_PNG
import dev.vighnesh.stackpage.io.MIME_WEBP
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
 * Output formats the platform can encode. HEIC and anything else the
 * platform can *decode* comes in through ImageSource without being listed
 * here; this enum is only about what goes out.
 */
enum class OutputFormat(val label: String, val extension: String, val mimeType: String) {
    JPG("JPG", "jpg", MIME_JPEG),
    PNG("PNG", "png", MIME_PNG),
    WEBP("WebP", "webp", MIME_WEBP),
    ;

    val compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            JPG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
        }
}

data class ConvertItem(
    val uri: Uri,
    val displayName: String,
    val originalBytes: Long,
    val status: ConvertStatus = ConvertStatus.Pending,
)

sealed interface ConvertStatus {
    data object Pending : ConvertStatus
    data class Done(val bytes: Long, val fileName: String) : ConvertStatus
    data class Failed(val message: String) : ConvertStatus
}

data class ConvertUiState(
    val items: List<ConvertItem> = emptyList(),
    val format: OutputFormat = OutputFormat.JPG,
    /** null means keep the original dimensions. */
    val preset: SizePreset? = null,
    val saving: Boolean = false,
    val savedCount: Int? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

class ConvertViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ConvertUiState())
    val state: StateFlow<ConvertUiState> = _state.asStateFlow()

    private var saveJob: Job? = null

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _state.value.items.map { it.uri }.toSet()
            val added = uris.filterNot { it in existing }.map { uri ->
                ConvertItem(uri, displayNameOf(uri), sizeOf(uri))
            }
            _state.update { it.copy(items = it.items + added, savedCount = null) }
        }
    }

    fun removeImage(uri: Uri) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.uri == uri }) }
    }

    fun clearAll() {
        _state.update { ConvertUiState(format = it.format, preset = it.preset) }
    }

    fun setFormat(format: OutputFormat) {
        _state.update { it.copy(format = format, savedCount = null) }
    }

    fun setPreset(preset: SizePreset?) {
        _state.update { it.copy(preset = preset, savedCount = null) }
    }

    fun saveAll(treeUri: Uri) {
        if (_state.value.items.isEmpty() || _state.value.saving) return
        saveJob?.cancel()
        _state.update { it.copy(saving = true, savedCount = null) }

        saveJob = viewModelScope.launch(Dispatchers.IO) {
            var saved = 0
            for (item in _state.value.items) {
                ensureActive()
                val status = runCatching { convertOne(item, treeUri) }
                    .getOrElse { e -> ConvertStatus.Failed(e.message ?: "Could not convert.") }
                if (status is ConvertStatus.Done) saved++
                _state.update { s ->
                    s.copy(items = s.items.map { if (it.uri == item.uri) it.copy(status = status) else it })
                }
            }
            _state.update { it.copy(saving = false, savedCount = saved) }
        }
    }

    private fun convertOne(item: ConvertItem, treeUri: Uri): ConvertStatus {
        val app = getApplication<Application>()
        val current = _state.value
        val decoded = ImageSource.decode(app, item.uri)
            ?: return ConvertStatus.Failed("Could not read the image.")

        val bytes: ByteArray
        try {
            val bitmap = current.preset?.let { preset ->
                val (w, h) = fitWithin(decoded.width, decoded.height, preset.width, preset.height)
                if (w == decoded.width && h == decoded.height) decoded
                else Bitmap.createScaledBitmap(decoded, w, h, true)
            } ?: decoded

            try {
                val out = ByteArrayOutputStream()
                bitmap.compress(current.format.compressFormat, ENCODE_QUALITY, out)
                bytes = out.toByteArray()
            } finally {
                if (bitmap !== decoded) bitmap.recycle()
            }
        } finally {
            decoded.recycle()
        }

        val name = convertedFileName(item.displayName, current.format, current.preset)
        val out = createInTree(app, treeUri, name, current.format.mimeType)
            ?: return ConvertStatus.Failed("Could not create the file.")
        app.contentResolver.openOutputStream(out)?.use { it.write(bytes) }
            ?: return ConvertStatus.Failed("Could not write the file.")
        return ConvertStatus.Done(bytes.size.toLong(), name)
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

    private companion object {
        /**
         * High-fidelity conversion, not compression: whoever wants small
         * files has the compress tool. PNG ignores the value entirely.
         */
        const val ENCODE_QUALITY = 90
    }
}

/** `photo.heic` to JPG at Square 1080 becomes `photo-1080x1080.jpg`. */
fun convertedFileName(sourceName: String?, format: OutputFormat, preset: SizePreset?): String {
    val base = (sourceName ?: "image").substringBeforeLast('.').ifBlank { "image" }
    val suffix = preset?.let { "-${it.width}x${it.height}" } ?: ""
    return "$base$suffix.${format.extension}"
}
