package dev.vighnesh.stackpage.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil3.compose.AsyncImage

/**
 * The page stack: a reorderable grid of thumbnails, one per PDF page.
 *
 * Reordering is long-press-and-drag, implemented directly against
 * [LazyGridState] rather than pulled in as a dependency. The whole interaction
 * is about sixty lines, and owning it means the drag threshold, the haptic, and
 * the swap rule stay tunable instead of being whatever a library decided.
 */
@Composable
fun PageGrid(
    images: List<Uri>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Uri) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current

    fun indexAt(position: Offset): Int? =
        state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            position.x >= item.offset.x && position.x <= item.offset.x + item.size.width &&
                position.y >= item.offset.y && position.y <= item.offset.y + item.size.height
        }?.index

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(images.size) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        indexAt(offset)?.let {
                            draggingIndex = it
                            dragPosition = offset
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        dragPosition += delta
                        val to = indexAt(dragPosition)
                        if (to != null && to != from) {
                            onMove(from, to)
                            draggingIndex = to
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = { draggingIndex = null },
                    onDragCancel = { draggingIndex = null },
                )
            },
    ) {
        items(images, key = { it.toString() }) { uri ->
            val index = images.indexOf(uri)
            PageThumbnail(
                uri = uri,
                pageNumber = index + 1,
                isDragging = draggingIndex == index,
                onRemove = { onRemove(uri) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun PageThumbnail(
    uri: Uri,
    pageNumber: Int,
    isDragging: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A small lift rather than a jump: the card should read as picked up, not
    // as a different element.
    val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, label = "dragScale")
    val elevation = if (isDragging) 12.dp else 0.dp

    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { contentDescription = "Page $pageNumber. Long press to reorder." },
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Page number, bottom-left, on its own scrim so it stays legible over
        // both a white document scan and a dark photo.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(topEnd = 10.dp, bottomStart = 14.dp),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                text = pageNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(percent = 50),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove page $pageNumber",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
        }
    }
}
