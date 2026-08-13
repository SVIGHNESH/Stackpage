package dev.vighnesh.stackpage.feature.sign

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.vighnesh.stackpage.feature.stack.formatBytes

@Composable
fun SignScreen(
    state: SignUiState,
    onPickPdf: () -> Unit,
    onPage: (Int) -> Unit,
    onStartDrawing: () -> Unit,
    onCancelDrawing: () -> Unit,
    onSaveSignature: (android.graphics.Bitmap) -> Unit,
    onPlacement: (Placement) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDismissResult: () -> Unit,
) {
    when {
        state.drawing -> DrawingScreen(onCancel = onCancelDrawing, onUse = onSaveSignature)
        state.source == null -> EmptyState(onPickPdf)
        state.signature == null -> NeedSignature(onStartDrawing)
        else -> PlacementScreen(state, onPage, onStartDrawing, onPlacement, onSave, onClear)
    }

    (state.status as? SignStatus.Done)?.let { done ->
        AlertDialog(
            onDismissRequest = onDismissResult,
            icon = {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            },
            title = { Text("Signed copy saved") },
            text = { Text("${formatBytes(done.byteSize)} · the original is untouched.", textAlign = TextAlign.Center) },
            confirmButton = { TextButton(onClick = onDismissResult) { Text("Done") } },
        )
    }
}

@Composable
private fun EmptyState(onPickPdf: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Draw,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Sign a PDF",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Draw your signature once, then drag it onto any page. " +
                    "The rest of the document is not re-saved as images.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = onPickPdf, modifier = Modifier.heightIn(min = 52.dp)) {
                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Choose a PDF", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Everything happens on this device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NeedSignature(onStartDrawing: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "First, your signature",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Draw it once; it is stored privately on this device and reused for every signing.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onStartDrawing, modifier = Modifier.heightIn(min = 52.dp)) {
                Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Draw signature")
            }
        }
    }
}

@Composable
private fun DrawingScreen(
    onCancel: () -> Unit,
    onUse: (android.graphics.Bitmap) -> Unit,
) {
    val strokes = remember { SignatureStrokes() }
    val density = LocalDensity.current

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Draw your signature", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Use your finger or a stylus, the way you would sign paper.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            SignaturePad(
                strokes = strokes,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = { strokes.clear() }) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        strokes.toBitmap(with(density) { 5.dp.toPx() })?.let(onUse)
                    },
                    enabled = !strokes.isEmpty,
                ) {
                    Text("Use signature")
                }
            }
        }
    }
}

@Composable
private fun PlacementScreen(
    state: SignUiState,
    onPage: (Int) -> Unit,
    onStartDrawing: () -> Unit,
    onPlacement: (Placement) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.sourceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Change") }
            }
            Spacer(Modifier.height(8.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val preview = state.pagePreview
                if (preview == null) {
                    CircularProgressIndicator()
                } else {
                    PageWithSignature(
                        preview = preview,
                        signature = state.signature!!,
                        placement = state.placement,
                        onPlacement = onPlacement,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Drag the signature into place · size below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.placement.widthFrac,
                onValueChange = { onPlacement(state.placement.copy(widthFrac = it)) },
                valueRange = 0.1f..0.6f,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { onPage(state.pageIndex - 1) }, enabled = state.pageIndex > 0) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous page")
                }
                Text(
                    "Page ${state.pageIndex + 1} of ${state.pageCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(
                    onClick = { onPage(state.pageIndex + 1) },
                    enabled = state.pageIndex < state.pageCount - 1,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next page")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onStartDrawing) { Text("Redraw") }
                Button(
                    onClick = onSave,
                    enabled = state.status != SignStatus.Working,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (state.status == SignStatus.Working) "Signing…" else "Save signed copy")
                }
            }
            (state.status as? SignStatus.Failed)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The rendered page with the draggable signature overlay. */
@Composable
private fun PageWithSignature(
    preview: android.graphics.Bitmap,
    signature: android.graphics.Bitmap,
    placement: Placement,
    onPlacement: (Placement) -> Unit,
) {
    var container by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .aspectRatio(preview.width.toFloat() / preview.height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .onSizeChanged { container = it }
            .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    if (container == IntSize.Zero) return@detectDragGestures
                    val heightFrac = placement.widthFrac *
                        (signature.height.toFloat() / signature.width) *
                        (container.width.toFloat() / container.height)
                    onPlacement(
                        placement.copy(
                            leftFrac = (placement.leftFrac + delta.x / container.width)
                                .coerceIn(0f, 1f - placement.widthFrac),
                            topFrac = (placement.topFrac + delta.y / container.height)
                                .coerceIn(0f, (1f - heightFrac).coerceAtLeast(0f)),
                        ),
                    )
                }
            },
    ) {
        Image(
            bitmap = preview.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (container != IntSize.Zero) {
            val density = LocalDensity.current
            val widthDp = with(density) { (placement.widthFrac * container.width).toDp() }
            val xDp = with(density) { (placement.leftFrac * container.width).toDp() }
            val yDp = with(density) { (placement.topFrac * container.height).toDp() }
            Image(
                bitmap = signature.asImageBitmap(),
                contentDescription = "Signature. Drag to place.",
                modifier = Modifier
                    .padding(start = xDp, top = yDp)
                    .width(widthDp),
            )
        }
    }
}
