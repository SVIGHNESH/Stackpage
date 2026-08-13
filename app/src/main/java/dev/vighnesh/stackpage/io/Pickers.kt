package dev.vighnesh.stackpage.io

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The two system launchers the app lives on, wrapped so screens ask for
 * "a picker" rather than owning ActivityResult plumbing.
 *
 * Both are permission-free by design: the Photo Picker shows only what the
 * user chooses, and SAF CreateDocument writes only where the user pointed.
 * That is the app's entire privacy story, so keep it here in one place.
 */

/**
 * The picker's own ceiling per launch. Well past what a device can hold in
 * one PDF, but a hard number is better than an unbounded pick that OOMs later.
 */
const val MAX_IMAGES_PER_PICK = 100

/** Returns a launch function for the multi-image Photo Picker. */
@Composable
fun rememberImagePicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_PICK),
    ) { uris ->
        // Persist read access so the URIs survive process death between
        // picking and exporting. Some providers refuse; that is fine, the
        // grant then only lasts the session.
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        onPicked(uris)
    }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

/** Returns a launch function for picking an existing PDF via SAF. */
@Composable
fun rememberPdfPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPicked(it)
        }
    }
    return { launcher.launch(arrayOf("application/pdf")) }
}

/** Returns a launch function for picking an output folder via SAF. */
@Composable
fun rememberDirectoryPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree -> tree?.let(onPicked) }
    return { launcher.launch(null) }
}

/** Returns a launch function taking the suggested file name for a new PDF. */
@Composable
fun rememberPdfCreator(onCreated: (Uri) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { target -> target?.let(onCreated) }
    return { suggestedName -> launcher.launch(suggestedName) }
}
