package dev.vighnesh.stackpage.feature.protect

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProtectStatus {
    data object Idle : ProtectStatus
    data object Working : ProtectStatus
    data class Done(val byteSize: Long) : ProtectStatus
    data class Failed(val message: String) : ProtectStatus
}

data class ProtectUiState(
    val source: Uri? = null,
    val sourceName: String = "",
    val password: String = "",
    val status: ProtectStatus = ProtectStatus.Idle,
) {
    val canSave: Boolean get() = source != null && password.length >= 4 && status != ProtectStatus.Working
}

class ProtectViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ProtectUiState())
    val state: StateFlow<ProtectUiState> = _state.asStateFlow()

    fun setSource(uri: Uri) {
        _state.update {
            it.copy(source = uri, sourceName = displayNameOf(uri), status = ProtectStatus.Idle)
        }
    }

    fun setPassword(password: String) {
        _state.update { it.copy(password = password) }
    }

    /** Suggested output name for the SAF dialog. */
    fun suggestedFileName(): String {
        val base = _state.value.sourceName.substringBeforeLast('.').ifBlank { "document" }
        return "$base-protected.pdf"
    }

    fun protectTo(target: Uri) {
        val current = _state.value
        val source = current.source ?: return
        if (!current.canSave) return

        _state.update { it.copy(status = ProtectStatus.Working) }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val status = runCatching {
                // Idempotent; PDFBox needs its font/glyph resources unpacked
                // once per process before any document work.
                PDFBoxResourceLoader.init(app)

                val document = app.contentResolver.openInputStream(source)?.use { input ->
                    PDDocument.load(input)
                } ?: return@runCatching ProtectStatus.Failed("Could not read the PDF.")

                document.use { doc ->
                    val policy = StandardProtectionPolicy(
                        current.password,
                        current.password,
                        AccessPermission(),
                    ).apply {
                        encryptionKeyLength = 128
                        setPreferAES(true)
                    }
                    doc.protect(policy)

                    app.contentResolver.openOutputStream(target)?.use { out ->
                        doc.save(out)
                    } ?: return@runCatching ProtectStatus.Failed("Could not write the file.")
                }

                val size = app.contentResolver.openFileDescriptor(target, "r")
                    ?.use { it.statSize.coerceAtLeast(0) } ?: 0L
                ProtectStatus.Done(size)
            }.getOrElse { e ->
                ProtectStatus.Failed(
                    if (e.message?.contains("password", ignoreCase = true) == true) {
                        "This PDF already has a password."
                    } else {
                        e.message ?: "Could not protect the PDF."
                    },
                )
            }
            _state.update { it.copy(status = status) }
        }
    }

    fun dismissResult() {
        _state.update { it.copy(status = ProtectStatus.Idle) }
    }

    fun clear() {
        _state.update { ProtectUiState() }
    }

    private fun displayNameOf(uri: Uri): String {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst() && !it.isNull(0)) return it.getString(0) }
        return "document.pdf"
    }
}
