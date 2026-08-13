package dev.vighnesh.stackpage.feature.scan

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.vighnesh.stackpage.feature.stack.StackViewModel

/**
 * Paper to pages, via the ML Kit document scanner.
 *
 * The camera, corner detection, crop, and filter surfaces are the scanner's
 * own Play-services-hosted activity - that is why this app still declares no
 * permissions, and it also means that UI is Google's, not ours. What is ours
 * is everything around it: a proper landing screen instead of a blank
 * spinner, a deliberate start button instead of an ambush launch, and a
 * calm return whether the user scanned or backed out.
 *
 * The offline spike from docs/PLAN.md is still owed. See decision 14.
 */
@Composable
fun ScanRoute(onScanned: () -> Unit) {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity)
    // The same activity-scoped view model the stack tool uses, so scanned
    // pages join whatever is already on the pile.
    val stackVm: StackViewModel = viewModel(viewModelStoreOwner = activity)

    var launching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        launching = false
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            ?.pages.orEmpty().mapNotNull { it.imageUri }
        if (pages.isNotEmpty()) {
            stackVm.addImages(pages)
            onScanned()
        }
        // Backing out of the camera is not an error; stay here quietly.
    }

    fun startScan() {
        launching = true
        error = null
        val options = GmsDocumentScannerOptions.Builder()
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
                launching = false
                error = "The scanner needs a one-time download from Google Play services. " +
                    "Connect to the internet once and try again."
            }
    }

    ScanScreen(
        launching = launching,
        error = error,
        onStartScan = ::startScan,
    )
}

@Composable
private fun ScanScreen(
    launching: Boolean,
    error: String?,
    onStartScan: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Scan paper\ninto pages",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Point the camera at a document. Corners are found for you, " +
                    "and you can straighten, filter, and add more pages before they " +
                    "land in your PDF stack.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStartScan,
                enabled = !launching,
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                if (launching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Opening camera…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Start scanning", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "The camera screen is provided by Google Play services. " +
                    "Scanned pages stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Matches the image picker's ceiling; one number for "too many pages". */
private const val PAGE_LIMIT = 100
