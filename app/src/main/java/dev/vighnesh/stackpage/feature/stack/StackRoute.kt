package dev.vighnesh.stackpage.feature.stack

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vighnesh.stackpage.io.openFile
import dev.vighnesh.stackpage.io.rememberImagePicker
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
    val createPdf = rememberPdfCreator(onCreated = vm::export)

    StackScreen(
        state = state,
        onPickImages = pickImages,
        onMove = vm::move,
        onRemove = vm::removeImage,
        onRotate = vm::rotatePage,
        onClearAll = vm::clearAll,
        onPageSize = vm::setPageSize,
        onOrientation = vm::setOrientation,
        onMargin = vm::setMargin,
        onExport = { createPdf(vm.suggestedFileName()) },
        onCancelExport = vm::cancelExport,
        onDismissResult = vm::dismissExportResult,
        onOpenResult = { uri -> openFile(context, uri) },
        onShareResult = { uri -> shareFile(context, uri) },
    )
}
