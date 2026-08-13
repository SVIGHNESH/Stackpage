package dev.vighnesh.stackpage.feature.sign

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Ink on paper, literally: strokes are captured as point lists so they can
 * be redrawn on a transparent Android bitmap when the user keeps them.
 */
class SignatureStrokes {
    val strokes = mutableStateListOf<List<Offset>>()

    val isEmpty: Boolean get() = strokes.isEmpty()

    fun clear() {
        strokes.clear()
    }

    /** Renders the strokes onto a transparent bitmap, cropped to their bounds. */
    fun toBitmap(strokeWidthPx: Float): Bitmap? {
        val points = strokes.flatten()
        if (points.isEmpty()) return null
        val pad = strokeWidthPx * 2
        val minX = points.minOf { it.x } - pad
        val minY = points.minOf { it.y } - pad
        val maxX = points.maxOf { it.x } + pad
        val maxY = points.maxOf { it.y } + pad
        val width = (maxX - minX).toInt().coerceAtLeast(1)
        val height = (maxY - minY).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            val path = AndroidPath()
            path.moveTo(stroke.first().x - minX, stroke.first().y - minY)
            for (p in stroke.drop(1)) path.lineTo(p.x - minX, p.y - minY)
            canvas.drawPath(path, paint)
        }
        return bitmap
    }
}

@Composable
fun SignaturePad(
    strokes: SignatureStrokes,
    modifier: Modifier = Modifier,
) {
    val currentStroke = remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        currentStroke.value = listOf(start)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke.value = currentStroke.value + change.position
                    },
                    onDragEnd = {
                        if (currentStroke.value.size > 1) strokes.strokes.add(currentStroke.value)
                        currentStroke.value = emptyList()
                    },
                    onDragCancel = { currentStroke.value = emptyList() },
                )
            },
    ) {
        for (stroke in strokes.strokes + listOf(currentStroke.value)) {
            if (stroke.size < 2) continue
            val path = Path().apply {
                moveTo(stroke.first().x, stroke.first().y)
                for (p in stroke.drop(1)) lineTo(p.x, p.y)
            }
            drawPath(
                path,
                Color.Black,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
