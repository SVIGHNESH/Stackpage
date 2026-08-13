package dev.vighnesh.stackpage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vighnesh.stackpage.feature.stack.StackScreen
import dev.vighnesh.stackpage.feature.stack.StackViewModel
import dev.vighnesh.stackpage.ui.theme.StackpageTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StackpageTheme {
                val vm: StackViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current

                // The Photo Picker needs no storage permission and shows only
                // what the user chooses, which is the whole point of not asking
                // for READ_MEDIA_IMAGES in a privacy-first utility.
                val pickImages = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
                ) { uris ->
                    uris.forEach { uri ->
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                    vm.addImages(uris)
                }

                // SAF: the user names the file and picks the folder, so the app
                // writes exactly where they said and nowhere else.
                val createDocument = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/pdf"),
                ) { target -> target?.let(vm::export) }

                StackScreen(
                    state = state,
                    onPickImages = {
                        pickImages.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onMove = vm::move,
                    onRemove = vm::removeImage,
                    onClearAll = vm::clearAll,
                    onPageSize = vm::setPageSize,
                    onOrientation = vm::setOrientation,
                    onMargin = vm::setMargin,
                    onExport = { createDocument.launch(vm.suggestedFileName()) },
                    onCancelExport = vm::cancelExport,
                    onDismissResult = vm::dismissExportResult,
                    onOpenResult = { uri ->
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                "No PDF viewer installed.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onShareResult = { uri -> share(uri) },
                )
            }
        }
    }

    private fun share(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    private companion object {
        /**
         * The picker's own ceiling. Well past what a phone can hold in one PDF,
         * but a hard number is better than an unbounded pick that OOMs later.
         */
        const val MAX_IMAGES = 100
    }
}
