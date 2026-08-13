package dev.vighnesh.stackpage.feature.scan

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.vighnesh.stackpage.feature.stack.StackViewModel

/**
 * Paper to pages, via the ML Kit document scanner.
 *
 * The scanner is a Play-services-hosted activity that owns its own camera
 * session, corner detection, crop UI, and filters. That is why this app still
 * declares no permissions - CAMERA belongs to the scanner's process, not
 * ours. The route is therefore thin: launch, collect page URIs, hand them to
 * the shared stack view model, and land the user in the stack tool.
 *
 * The offline spike from docs/PLAN.md is still owed: verify on hardware that
 * scanning needs no network at scan time. See docs/DECISIONS.md.
 */
@Composable
fun ScanRoute(
    onScanned: () -> Unit,
    onCancelled: () -> Unit,
) {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    val context = LocalContext.current
    // The same activity-scoped view model the stack tool uses, so scanned
    // pages join whatever is already on the pile.
    val stackVm: StackViewModel = viewModel(viewModelStoreOwner = activity)

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            ?.pages.orEmpty().mapNotNull { it.imageUri }
        if (pages.isNotEmpty()) {
            stackVm.addImages(pages)
            onScanned()
        } else {
            onCancelled()
        }
    }

    LaunchedEffect(Unit) {
        val options = GmsDocumentScannerOptions.Builder()
            // FULL mode is the scanner's own capture, corner adjustment,
            // and filter UI (including greyscale and B&W), which is exactly
            // the roadmap's scan-correction surface without us owning a
            // camera session.
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(PAGE_LIMIT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                // Old or de-Googled Play services. Say so instead of dying.
                Toast.makeText(
                    context,
                    "Document scanning needs a current Google Play services.",
                    Toast.LENGTH_LONG,
                ).show()
                onCancelled()
            }
    }

    // The scanner activity covers this within a moment; the spinner only
    // shows during the intent round-trip.
    Scaffold { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/** Matches the image picker's ceiling; one number for "too many pages". */
private const val PAGE_LIMIT = 100
