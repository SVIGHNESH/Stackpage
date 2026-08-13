package dev.vighnesh.stackpage.feature.compress

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vighnesh.stackpage.io.rememberDirectoryPicker
import dev.vighnesh.stackpage.io.rememberImagePicker

@Composable
fun CompressRoute() {
    // Activity-scoped for the same reason as StackRoute: a trip to home and
    // back must not drop the user's picked batch.
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    val vm: CompressViewModel = viewModel(viewModelStoreOwner = activity)
    val state by vm.state.collectAsStateWithLifecycle()

    val pickImages = rememberImagePicker(onPicked = vm::addImages)
    val pickFolder = rememberDirectoryPicker(onPicked = vm::saveAll)

    CompressScreen(
        state = state,
        onPickImages = pickImages,
        onRemove = vm::removeImage,
        onClearAll = vm::clearAll,
        onTarget = vm::setTarget,
        onSaveAll = pickFolder,
        onDismissResult = vm::dismissSaveResult,
    )
}
