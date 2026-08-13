package dev.vighnesh.stackpage.feature.compress

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.vighnesh.stackpage.feature.stack.formatBytes

/** The preset target chips, in bytes. */
private val TARGET_PRESETS = listOf(50_000L, 100_000L, 200_000L, 500_000L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
    state: CompressUiState,
    onPickImages: () -> Unit,
    onRemove: (Uri) -> Unit,
    onClearAll: () -> Unit,
    onTarget: (Long) -> Unit,
    onSaveAll: () -> Unit,
    onDismissResult: () -> Unit,
) {
    var showCustomTarget by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compress", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (!state.isEmpty) {
                        IconButton(onClick = onClearAll) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Remove all images")
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
                SaveBar(state = state, onSaveAll = onSaveAll)
            }
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyState(onPickImages, Modifier.padding(padding))
        } else {
            Column(Modifier.padding(padding)) {
                TargetChips(
                    targetBytes = state.targetBytes,
                    onTarget = onTarget,
                    onCustom = { showCustomTarget = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.uri.toString() }) { item ->
                        ItemRow(item = item, saving = state.saving, onRemove = { onRemove(item.uri) })
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = onPickImages, enabled = !state.saving) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Add more")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomTarget) {
        CustomTargetDialog(
            onDismiss = { showCustomTarget = false },
            onConfirm = { kb ->
                showCustomTarget = false
                onTarget(kb * 1000L)
            },
        )
    }

    state.savedCount?.let { saved ->
        AlertDialog(
            onDismissRequest = onDismissResult,
            icon = {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            },
            title = { Text(if (saved == state.items.size) "All saved" else "$saved of ${state.items.size} saved") },
            text = {
                Text(
                    "Compressed copies are in the folder you chose. The originals are untouched.",
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = { TextButton(onClick = onDismissResult) { Text("Done") } },
        )
    }
}

@Composable
private fun EmptyState(onPickImages: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Compress,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Shrink images\nto a size",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Pick images, choose a target like 200 KB, and save copies that fit it. Forms and portals stop complaining.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPickImages, modifier = Modifier.heightIn(min = 52.dp)) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Choose images", style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun TargetChips(
    targetBytes: Long,
    onTarget: (Long) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            "Target size",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TARGET_PRESETS.forEach { preset ->
                FilterChip(
                    selected = targetBytes == preset,
                    onClick = { onTarget(preset) },
                    label = { Text("${preset / 1000} KB") },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
            val isCustom = targetBytes !in TARGET_PRESETS
            FilterChip(
                selected = isCustom,
                onClick = onCustom,
                label = { Text(if (isCustom) "${targetBytes / 1000} KB" else "Custom") },
                modifier = Modifier.heightIn(min = 44.dp),
            )
        }
    }
}

@Composable
private fun ItemRow(item: CompressItem, saving: Boolean, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val detail = when (val s = item.status) {
                    is ItemStatus.Done ->
                        "${formatBytes(item.originalBytes)} → ${formatBytes(s.bytes)}" +
                            if (s.hitTarget) "" else " (best possible)"
                    is ItemStatus.Failed -> s.message
                    ItemStatus.Pending -> formatBytes(item.originalBytes)
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        is ItemStatus.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (item.status) {
                is ItemStatus.Done -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Saved",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp),
                )
                is ItemStatus.Failed -> Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                ItemStatus.Pending -> if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove ${item.displayName}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveBar(state: CompressUiState, onSaveAll: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
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
                    val count = state.items.size
                    Text(
                        if (count == 1) "1 image" else "$count images",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            state.estimating -> "Estimating…"
                            state.estimateBytes != null ->
                                "Largest will be ~${formatBytes(state.estimateBytes)}"
                            else -> "Target ${formatBytes(state.targetBytes)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onSaveAll,
                    enabled = !state.saving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (state.saving) "Saving…" else "Save all", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CustomTargetDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    val kb = text.toLongOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom target") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                label = { Text("Size in KB") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { kb?.let(onConfirm) },
                enabled = kb != null && kb > 0,
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
