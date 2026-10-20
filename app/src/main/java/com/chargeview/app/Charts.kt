package com.chargeview.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

data class Pt(val t: Long, val y: Float, val dir: Int)

private const val TICKS = 5

/**
 * Line chart with a labelled Y axis and touch inspection. Discharging runs are drawn in amber
 * in amber, charging runs solid in green.
 */
@Composable
fun LineChart(
    pts: List<Pt>,
    unit: String,
    zeroLine: Boolean,
    fixedRange: ClosedFloatingPointRange<Float>?,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
    logTime: Boolean = false,
) {
    if (pts.size < 2) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Collecting data…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var sel by remember { mutableStateOf<Float?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val green = chargeGreen()
    val amber = dischargeAmber()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.onSurfaceVariant

    val axisPx = with(density) { 40.dp.toPx() }
    val rightPx = with(density) { 8.dp.toPx() }
    val topPx = with(density) { 6.dp.toPx() }
    val bottomPx = with(density) { 20.dp.toPx() }
    val strokePx = with(density) { 1.5.dp.toPx() }

    val t0 = pts.first().t
    val t1 = pts.last().t
    val span = (t1 - t0).coerceAtLeast(1L)

    var yMin: Float
    var yMax: Float
    if (fixedRange != null) {
        yMin = fixedRange.start
        yMax = fixedRange.endInclusive
    } else {
        yMin = pts.minOf { it.y }
        yMax = pts.maxOf { it.y }
        if (zeroLine) {
            yMin = minOf(yMin, 0f)
            yMax = maxOf(yMax, 0f)
        }
        if (yMax - yMin < 1f) {
            yMax += 0.5f
            yMin -= 0.5f
        }
        val pad = (yMax - yMin) * 0.1f
        yMax += pad
        if (!(zeroLine && yMin == 0f)) yMin -= pad
    }

    if (integer && fixedRange == null) {
        yMin = floor(yMin)
        val step = ceil((ceil(yMax) - yMin) / (TICKS - 1)).coerceAtLeast(1f)
        yMax = yMin + step * (TICKS - 1)
    }

    val plotW = (size.width - axisPx - rightPx).coerceAtLeast(1f)
    val plotH = (size.height - topPx - bottomPx).coerceAtLeast(1f)
    // Log axis: "Now" sits at the right edge and older readings are compressed towards the left.
    val logK = 10f
    val logSpan = ln(1f + span / 1000f / logK)
    fun xOf(t: Long) =
        if (logTime) axisPx + (1f - ln(1f + (t1 - t) / 1000f / logK) / logSpan) * plotW
        else axisPx + (t - t0).toFloat() / span * plotW
    fun yOf(v: Float) = topPx + (1f - (v - yMin) / (yMax - yMin)) * plotH

    val selected: Pt? = sel?.let { x ->
        val frac = ((x - axisPx) / plotW).coerceIn(0f, 1f)
        val tm = if (logTime) t1 - ((exp((1f - frac) * logSpan) - 1f) * logK * 1000f).toLong()
        else t0 + (frac * span).toLong()
        pts.minByOrNull { abs(it.t - tm) }
    }

    Column(modifier) {
        val readout = selected?.let {
            val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(it.t))
            val state = when {
                it.dir > 0 -> "Charging"
                it.dir < 0 -> "Discharging"
                else -> "Idle"
            }
            val sign = if (zeroLine && it.y > 0f) "+" else ""
            "$time · $sign${(if (integer) "%.0f" else "%.1f").format(it.y)} $unit · $state"
        } ?: "Touch the graph to inspect values"
        Text(
            readout,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected != null) MaterialTheme.colorScheme.onSurface else labelColor,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { size = it }
                    .pointerInput(pts) { detectTapGestures(onPress = { sel = it.x; tryAwaitRelease() }) }
                    .pointerInput(pts) {
                        detectDragGestures(onDragStart = { sel = it.x }, onDrag = { c, _ -> sel = c.position.x })
                    }
            ) {
                val style = TextStyle(fontSize = 10.sp, color = labelColor)
                for (k in 0 until TICKS) {
                    val v = yMin + (yMax - yMin) * k / (TICKS - 1)
                    val y = yOf(v)
                    drawLine(grid, Offset(axisPx, y), Offset(axisPx + plotW, y), 1f)
                    val txt = if (integer || yMax - yMin >= 10f) "%.0f".format(v) else "%.1f".format(v)
                    val layout = measurer.measure(txt, style)
                    drawText(
                        layout,
                        topLeft = Offset(axisPx - layout.size.width - 6f, y - layout.size.height / 2f)
                    )
                }
                if (logTime) {
                    listOf(10, 30, 60, 120, 300, 600, 1200, 1800, 3600).filter { it * 1000L < span }.forEach { s ->
                        val x = xOf(t1 - s * 1000L)
                        drawLine(grid, Offset(x, topPx), Offset(x, topPx + plotH), 1f)
                        val txt = measurer.measure(if (s < 60) "−${s}s" else "−${s / 60}m", style)
                        drawText(txt, topLeft = Offset(x - txt.size.width / 2f, size.height.toFloat() - txt.size.height))
                    }
                }
                if (zeroLine && yMin < 0f && yMax > 0f) {
                    drawLine(zeroColor, Offset(axisPx, yOf(0f)), Offset(axisPx + plotW, yOf(0f)), 2f)
                }

                var i = 0
                while (i < pts.size - 1) {
                    val neg = pts[i].dir < 0
                    val path = Path()
                    path.moveTo(xOf(pts[i].t), yOf(pts[i].y))
                    var j = i + 1
                    while (j < pts.size && (pts[j].dir < 0) == neg) {
                        path.lineTo(xOf(pts[j].t), yOf(pts[j].y))
                        j++
                    }
                    // Bridge to the next run so the line stays continuous.
                    if (j < pts.size) path.lineTo(xOf(pts[j].t), yOf(pts[j].y))
                    drawPath(
                        path,
                        if (neg) amber else green,
                        style = Stroke(width = strokePx)
                    )
                    i = j
                }

                selected?.let {
                    val x = xOf(it.t)
                    drawLine(labelColor, Offset(x, topPx), Offset(x, topPx + plotH), 2f)
                    drawCircle(Color.White, strokePx * 2.4f, Offset(x, yOf(it.y)))
                    drawCircle(
                        if (it.dir < 0) amber else green,
                        strokePx * 1.8f, Offset(x, yOf(it.y))
                    )
                }

                val left = measurer.measure("−${(span / 60_000L).coerceAtLeast(1)}m", style)
                val now = measurer.measure("Now", style)
                if (!logTime) drawText(left, topLeft = Offset(axisPx, size.height.toFloat() - left.size.height))
                drawText(now, topLeft = Offset(axisPx + plotW - now.size.width, size.height.toFloat() - now.size.height))
            }
        }
    }
}
