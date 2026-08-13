package dev.vighnesh.stackpage.feature.sign

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vighnesh.stackpage.io.rememberPdfPicker

@Composable
fun SignRoute() {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    val vm: SignViewModel = viewModel(viewModelStoreOwner = activity)
    val state by vm.state.collectAsStateWithLifecycle()

    val pickPdf = rememberPdfPicker(onPicked = vm::setSource)
    val createPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { target -> target?.let(vm::signTo) }

    SignScreen(
        state = state,
        onPickPdf = pickPdf,
        onPage = vm::setPage,
        onStartDrawing = vm::startDrawing,
        onCancelDrawing = vm::cancelDrawing,
        onSaveSignature = vm::saveSignature,
        onPlacement = vm::setPlacement,
        onSave = { createPdf.launch(vm.suggestedFileName()) },
        onClear = vm::clear,
        onDismissResult = vm::dismissResult,
    )
}
