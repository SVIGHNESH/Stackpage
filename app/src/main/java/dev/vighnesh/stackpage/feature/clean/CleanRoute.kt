package dev.vighnesh.stackpage.feature.clean

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vighnesh.stackpage.io.rememberDirectoryPicker
import dev.vighnesh.stackpage.io.rememberImagePicker

@Composable
fun CleanRoute() {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    val vm: CleanViewModel = viewModel(viewModelStoreOwner = activity)
    val state by vm.state.collectAsStateWithLifecycle()

    val pickImages = rememberImagePicker(onPicked = vm::addImages)
    val pickFolder = rememberDirectoryPicker(onPicked = vm::saveAll)

    CleanScreen(
        state = state,
        onPickImages = pickImages,
        onRemove = vm::removeImage,
        onClearAll = vm::clearAll,
        onSaveAll = pickFolder,
        onDismissResult = vm::dismissSaveResult,
    )
}
