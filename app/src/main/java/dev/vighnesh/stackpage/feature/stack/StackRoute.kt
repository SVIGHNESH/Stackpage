package dev.vighnesh.stackpage.feature.stack

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vighnesh.stackpage.io.openFile
import dev.vighnesh.stackpage.io.rememberImagePicker
import dev.vighnesh.stackpage.io.rememberPdfPicker
import dev.vighnesh.stackpage.io.rememberPdfCreator
import dev.vighnesh.stackpage.io.shareFile

/**
 * Wires the stack screen to its view model and the system launchers, so the
 * nav host stays a table of routes and the screen stays a pure function of
 * state.
 */
@Composable
fun StackRoute() {
    // Scoped to the activity, not the back-stack entry, so leaving for home
    // and coming back keeps the page stack. The pre-navigation app behaved
    // this way, and M1 is a refactor with zero behaviour change.
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    val vm: StackViewModel = viewModel(viewModelStoreOwner = activity)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickImages = rememberImagePicker(onPicked = vm::addImages)
    val pickPdf = rememberPdfPicker(onPicked = vm::importPdf)
    val createPdf = rememberPdfCreator(onCreated = vm::export)

    // Notices (the rasterisation note, import failures) surface once as a
    // toast and are cleared so rotation does not replay them.
    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.dismissNotice()
        }
    }

    StackScreen(
        state = state,
        onPickImages = pickImages,
        onPickPdf = pickPdf,
        onMove = vm::move,
        onRemove = vm::removeImage,
        onRotate = vm::rotatePage,
        onClearAll = vm::clearAll,
        onPageSize = vm::setPageSize,
        onOrientation = vm::setOrientation,
        onMargin = vm::setMargin,
        onExport = { createPdf(vm.suggestedFileName()) },
        onCancelExport = vm::cancelExport,
        onCancelImport = vm::cancelImport,
        onDismissResult = vm::dismissExportResult,
        onOpenResult = { uri -> openFile(context, uri) },
        onShareResult = { uri -> shareFile(context, uri) },
    )
}
