package dev.vighnesh.stackpage.feature.stack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.vighnesh.stackpage.image.ImageSource
import dev.vighnesh.stackpage.pdf.CropRect
import dev.vighnesh.stackpage.pdf.PageSpec
import dev.vighnesh.stackpage.pdf.applySpec
import dev.vighnesh.stackpage.pdf.displayToSourceCrop
import dev.vighnesh.stackpage.pdf.normalizeRotation
import dev.vighnesh.stackpage.pdf.sourceToDisplayCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Crop and rotate for one page, full screen.
 *
 * The image is decoded with the rotation baked in, so what the user drags
 * over is exactly what the page will look like; the drag happens in display
 * fractions and only [displayToSourceCrop] knows the export stores crops in
 * pre-rotation source space. Rotating inside the editor resets the crop,
 * because a rect drawn on one orientation means nothing on another.
 */
@Composable
fun CropEditor(
    page: PageSpec,
    onCancel: () -> Unit,
    onApply: (rotationDegrees: Int, crop: CropRect?) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val context = LocalContext.current

    var rotation by remember(page) { mutableStateOf(normalizeRotation(page.rotationDegrees)) }
    // Display-space fractions, full image unless the page already has a crop.
    var crop by remember(page) {
        mutableStateOf(
            page.crop?.let { sourceToDisplayCrop(it, normalizeRotation(page.rotationDegrees)) }
                ?: CropRect(0f, 0f, 1f, 1f),
        )
    }

    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(page.uri, rotation) {
        bitmap = withContext(Dispatchers.IO) {
            ImageSource.decode(context, page.uri, maxEdge = PREVIEW_MAX_EDGE)
                ?.applySpec(PageSpec(page.uri, rotationDegrees = rotation))
                ?.asImageBitmap()
        }
    }
    // No explicit recycle: bitmaps are GC-managed on 26+, and recycling one
    // the Image composable may still draw during dispose is a crash.

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close editor", tint = Color.White)
                }
                Text(
                    "Edit page",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val full = crop.left < FULL_EPSILON && crop.top < FULL_EPSILON &&
                            crop.right > 1f - FULL_EPSILON && crop.bottom > 1f - FULL_EPSILON
                        onApply(rotation, if (full) null else displayToSourceCrop(crop, rotation))
                    },
                ) {
                    Text("Apply", style = MaterialTheme.typography.labelLarge)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val current = bitmap
                if (current == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    CropCanvas(
                        image = current,
                        crop = crop,
                        onCrop = { crop = it },
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                TextButton(onClick = {
                    rotation = normalizeRotation(rotation + 90)
                    crop = CropRect(0f, 0f, 1f, 1f)
                }) {
                    Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rotate")
                }
                TextButton(onClick = { crop = CropRect(0f, 0f, 1f, 1f) }) {
                    Text("Reset crop")
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The image with the draggable crop overlay on top. */
@Composable
private fun CropCanvas(
    image: ImageBitmap,
    crop: CropRect,
    onCrop: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var container by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadius = with(density) { 24.dp.toPx() }
    val minSpan = 0.08f

    // Where the fitted image actually sits inside the container.
    fun drawnRect(): Rect {
        if (container == IntSize.Zero) return Rect.Zero
        val scale = minOf(
            container.width.toFloat() / image.width,
            container.height.toFloat() / image.height,
        )
        val w = image.width * scale
        val h = image.height * scale
        val left = (container.width - w) / 2f
        val top = (container.height - h) / 2f
        return Rect(left, top, left + w, top + h)
    }

    fun toFraction(position: Offset): Offset {
        val rect = drawnRect()
        if (rect.width <= 0f || rect.height <= 0f) return Offset.Zero
        return Offset(
            ((position.x - rect.left) / rect.width).coerceIn(0f, 1f),
            ((position.y - rect.top) / rect.height).coerceIn(0f, 1f),
        )
    }

    Box(modifier.onSizeChanged { container = it }) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(image) {
                    var mode: DragMode? = null
                    detectDragGestures(
                        onDragStart = { start ->
                            val rect = drawnRect()
                            val corners = listOf(
                                DragMode.TOP_LEFT to Offset(
                                    rect.left + crop.left * rect.width,
                                    rect.top + crop.top * rect.height,
                                ),
                                DragMode.TOP_RIGHT to Offset(
                                    rect.left + crop.right * rect.width,
                                    rect.top + crop.top * rect.height,
                                ),
                                DragMode.BOTTOM_LEFT to Offset(
                                    rect.left + crop.left * rect.width,
                                    rect.top + crop.bottom * rect.height,
                                ),
                                DragMode.BOTTOM_RIGHT to Offset(
                                    rect.left + crop.right * rect.width,
                                    rect.top + crop.bottom * rect.height,
                                ),
                            )
                            val nearest = corners.minByOrNull { (_, c) -> (c - start).getDistance() }
                            mode = when {
                                nearest != null &&
                                    (nearest.second - start).getDistance() <= handleRadius * 1.6f ->
                                    nearest.first
                                toFraction(start).let {
                                    it.x in crop.left..crop.right && it.y in crop.top..crop.bottom
                                } -> DragMode.MOVE
                                else -> null
                            }
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val rect = drawnRect()
                            if (rect.width <= 0f || mode == null) return@detectDragGestures
                            val dx = delta.x / rect.width
                            val dy = delta.y / rect.height
                            val c = crop
                            val next = when (mode) {
                                DragMode.TOP_LEFT -> c.copyClamped(
                                    left = c.left + dx, top = c.top + dy, minSpan = minSpan,
                                )
                                DragMode.TOP_RIGHT -> c.copyClamped(
                                    right = c.right + dx, top = c.top + dy, minSpan = minSpan,
                                )
                                DragMode.BOTTOM_LEFT -> c.copyClamped(
                                    left = c.left + dx, bottom = c.bottom + dy, minSpan = minSpan,
                                )
                                DragMode.BOTTOM_RIGHT -> c.copyClamped(
                                    right = c.right + dx, bottom = c.bottom + dy, minSpan = minSpan,
                                )
                                DragMode.MOVE -> {
                                    val w = c.right - c.left
                                    val h = c.bottom - c.top
                                    val left = (c.left + dx).coerceIn(0f, 1f - w)
                                    val top = (c.top + dy).coerceIn(0f, 1f - h)
                                    CropRect(left, top, left + w, top + h)
                                }
                                null -> c
                            }
                            onCrop(next)
                        },
                        onDragEnd = { mode = null },
                        onDragCancel = { mode = null },
                    )
                },
        ) {
            val rect = drawnRect()
            if (rect == Rect.Zero) return@Canvas
            val cropPx = Rect(
                rect.left + crop.left * rect.width,
                rect.top + crop.top * rect.height,
                rect.left + crop.right * rect.width,
                rect.top + crop.bottom * rect.height,
            )
            val scrim = Color.Black.copy(alpha = 0.55f)
            // Four rectangles around the crop; a single path with even-odd
            // fill would also do, but this reads plainly.
            drawRect(scrim, Offset(rect.left, rect.top), Size(rect.width, cropPx.top - rect.top))
            drawRect(scrim, Offset(rect.left, cropPx.bottom), Size(rect.width, rect.bottom - cropPx.bottom))
            drawRect(scrim, Offset(rect.left, cropPx.top), Size(cropPx.left - rect.left, cropPx.height))
            drawRect(scrim, Offset(cropPx.right, cropPx.top), Size(rect.right - cropPx.right, cropPx.height))

            drawRect(
                Color.White,
                topLeft = cropPx.topLeft,
                size = cropPx.size,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
            for (corner in listOf(cropPx.topLeft, Offset(cropPx.right, cropPx.top),
                    Offset(cropPx.left, cropPx.bottom), cropPx.bottomRight)) {
                drawCircle(Color.White, radius = 7.dp.toPx(), center = corner)
                drawCircle(Color(0xFF1E3A5F), radius = 4.5f.dp.toPx(), center = corner)
            }
        }
    }
}

private enum class DragMode { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

/** Clamped copy that keeps edges ordered with a minimum span. */
private fun CropRect.copyClamped(
    left: Float = this.left,
    top: Float = this.top,
    right: Float = this.right,
    bottom: Float = this.bottom,
    minSpan: Float,
): CropRect {
    val l = left.coerceIn(0f, this.right - minSpan)
    val r = right.coerceIn(this.left + minSpan, 1f)
    val t = top.coerceIn(0f, this.bottom - minSpan)
    val b = bottom.coerceIn(this.top + minSpan, 1f)
    return CropRect(l, t, r, b)
}

private const val PREVIEW_MAX_EDGE = 1600
private const val FULL_EPSILON = 0.005f
