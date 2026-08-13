package dev.vighnesh.stackpage.feature.convert

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vighnesh.stackpage.io.rememberDirectoryPicker
import dev.vighnesh.stackpage.io.rememberImagePicker

@Composable
fun ConvertRoute() {
    // Activity-scoped like the other tools: a trip to home must not drop state.
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    val vm: ConvertViewModel = viewModel(viewModelStoreOwner = activity)
    val state by vm.state.collectAsStateWithLifecycle()

    val pickImages = rememberImagePicker(onPicked = vm::addImages)
    val pickFolder = rememberDirectoryPicker(onPicked = vm::saveAll)

    ConvertScreen(
        state = state,
        onPickImages = pickImages,
        onRemove = vm::removeImage,
        onClearAll = vm::clearAll,
        onFormat = vm::setFormat,
        onPreset = vm::setPreset,
        onSaveAll = pickFolder,
        onDismissResult = vm::dismissSaveResult,
    )
}
