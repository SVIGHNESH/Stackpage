package dev.vighnesh.stackpage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import android.net.Uri
import dev.vighnesh.stackpage.pdf.ExportOptions
import dev.vighnesh.stackpage.pdf.Margin
import dev.vighnesh.stackpage.pdf.PageOrientation
import dev.vighnesh.stackpage.pdf.PageSize

/**
 * One screen, one job. There is no navigation because there is one flow:
 * pick images, order them, export. A bottom nav bar here would be furniture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onPickImages: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Uri) -> Unit,
    onClearAll: () -> Unit,
    onPageSize: (PageSize) -> Unit,
    onOrientation: (PageOrientation) -> Unit,
    onMargin: (Margin) -> Unit,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismissResult: () -> Unit,
    onOpenResult: (Uri) -> Unit,
    onShareResult: (Uri) -> Unit,
) {
    var showOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stackpage", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (!state.isEmpty) {
                        IconButton(onClick = onClearAll) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Remove all pages")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (!state.isEmpty) {
                ExportBar(
                    pageCount = state.images.size,
                    options = state.options,
                    onOpenOptions = { showOptions = true },
                    onExport = onExport,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (state.isEmpty) {
                EmptyState(onPickImages = onPickImages, modifier = Modifier.padding(padding))
            } else {
                PageGrid(
                    images = state.images,
                    onMove = onMove,
                    onRemove = onRemove,
                    // The grid scrolls under the bars, so the padding goes on the
                    // content rather than the container.
                    contentPadding = PaddingValues(
                        start = 16.dp + padding.calculateStartPadding(LayoutDirection.Ltr),
                        end = 16.dp + padding.calculateEndPadding(LayoutDirection.Ltr),
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                )
                AddMoreButton(
                    onClick = onPickImages,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding() + 16.dp),
                )
            }

            when (val export = state.export) {
                is ExportState.Running -> ExportProgress(export, onCancelExport)
                is ExportState.Done -> ExportSuccess(export, onDismissResult, onOpenResult, onShareResult)
                is ExportState.Failed -> ExportFailure(export.message, onDismissResult)
                ExportState.Idle -> Unit
            }
        }
    }

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            OptionsSheet(
                options = state.options,
                onPageSize = onPageSize,
                onOrientation = onOrientation,
                onMargin = onMargin,
            )
        }
    }
}

@Composable
private fun EmptyState(onPickImages: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.PictureAsPdf,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Stack images\ninto a PDF",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Pick photos or scans, drag them into the order you want, and export one document.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPickImages,
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Choose images", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(20.dp))
        // The one claim worth making on this screen, stated plainly rather than
        // sold as a feature badge.
        Text(
            "Everything happens on this device. Nothing is uploaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddMoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add more", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ExportBar(
    pageCount: Int,
    options: ExportOptions,
    onOpenOptions: () -> Unit,
    onExport: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pageCount == 1) "1 page" else "$pageCount pages",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${options.pageSize.label} · ${options.orientation.label} · ${options.margin.label} margin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenOptions, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Export options")
                }
                Button(onClick = onExport, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Export PDF", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsSheet(
    options: ExportOptions,
    onPageSize: (PageSize) -> Unit,
    onOrientation: (PageOrientation) -> Unit,
    onMargin: (Margin) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Page setup", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        ChipRow(
            label = "Page size",
            entries = PageSize.entries,
            selected = options.pageSize,
            labelOf = { it.label },
            onSelect = onPageSize,
        )
        Spacer(Modifier.height(20.dp))

        ChipRow(
            label = "Orientation",
            entries = PageOrientation.entries,
            selected = options.orientation,
            labelOf = { it.label },
            onSelect = onOrientation,
            enabled = options.pageSize != PageSize.FIT_IMAGE,
            disabledNote = "A page that fits the image takes the image's own orientation.",
        )
        Spacer(Modifier.height(20.dp))

        ChipRow(
            label = "Margin",
            entries = Margin.entries,
            selected = options.margin,
            labelOf = { it.label },
            onSelect = onMargin,
        )
    }
}

@Composable
private fun <T> ChipRow(
    label: String,
    entries: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    disabledNote: String? = null,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEach { entry ->
                FilterChip(
                    selected = entry == selected,
                    onClick = { onSelect(entry) },
                    enabled = enabled,
                    label = { Text(labelOf(entry)) },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
        }
        if (!enabled && disabledNote != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                disabledNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExportProgress(state: ExportState.Running, onCancel: () -> Unit) {
    Scrim {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text("Building your PDF", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Page ${state.done.coerceAtLeast(1)} of ${state.total}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ExportSuccess(
    state: ExportState.Done,
    onDismiss: () -> Unit,
    onOpen: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
) {
    Scrim {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            // The only green in the app.
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("PDF saved", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "${state.pageCount} ${if (state.pageCount == 1) "page" else "pages"} · ${formatBytes(state.byteSize)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onShare(state.target) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Share")
                }
                Button(onClick = { onOpen(state.target) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open")
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

@Composable
private fun ExportFailure(message: String, onDismiss: () -> Unit) {
    Scrim {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text(
                "Export failed",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
        }
    }
}

/** Blocking overlay with a card, used for the three export states. */
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize()) {}
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(32.dp)
                .width(320.dp),
        ) {
            content()
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}

private val PageOrientation.label: String
    get() = when (this) {
        PageOrientation.PORTRAIT -> "Portrait"
        PageOrientation.LANDSCAPE -> "Landscape"
        PageOrientation.AUTO -> "Auto"
    }
