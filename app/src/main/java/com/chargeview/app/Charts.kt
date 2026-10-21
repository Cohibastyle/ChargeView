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
    val shortClock = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
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

    // On graphs with a zero line the colour follows the sign of the value, so a line that crosses
    // zero changes colour at the crossing instead of keeping the direction it entered with.
    // Graphs without a zero line colour by charge direction.
    val colorByValue = zeroLine && yMin < 0f && yMax > 0f

    // Instants on the [stepMs] grid, walking back from "now", thinned so kept marks are at least
    // [minGapPx] apart on screen. In log time the older end is compressed, so a fixed time step
    // there would collapse into a solid band; the gap test drops those marks instead.
    fun marks(stepMs: Long, minGapPx: Float): List<Long> {
        if (stepMs <= 0L || span / stepMs > 4000L) return emptyList()
        val out = ArrayList<Long>()
        var prev = Float.MAX_VALUE
        var s = t1 / stepMs * stepMs
        while (s > t0) {
            if (s > t0 && s < t1) {
                val x = xOf(s)
                if (prev - x >= minGapPx) {
                    out += s
                    prev = x
                }
            }
            s -= stepMs
        }
        return out
    }

    // Labelled linear grid: every minute while the window allows, widening over 1 h / 6 h / All.
    // A wide gap keeps the clock labels from touching each other.
    val linearMarks = if (logTime) emptyList() else
        marks(
            listOf(60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L)
                .firstOrNull { marks(it, 52f).size >= 2 } ?: 60_000L,
            52f,
        )

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
                } else {
                    // Linear time: a labelled vertical line on every whole minute (widening to
                    // 5 / 15 / 30 minutes over the 1 h, 6 h and All windows, where minute lines
                    // would crowd together). Labels are skipped if they would overlap.
                    var lastRight = -Float.MAX_VALUE
                    linearMarks.asReversed().forEach { t ->
                        val x = xOf(t)
                        drawLine(labelColor, Offset(x, topPx), Offset(x, topPx + plotH), 1f)
                        val txt = measurer.measure(shortClock.format(Date(t)), style)
                        val left = x - txt.size.width / 2f
                        if (left > lastRight + 6f && left >= axisPx) {
                            drawText(txt, topLeft = Offset(left, size.height.toFloat() - txt.size.height))
                            lastRight = left + txt.size.width
                        }
                    }
                }
                if (zeroLine && yMin < 0f && yMax > 0f) {
                    drawLine(zeroColor, Offset(axisPx, yOf(0f)), Offset(axisPx + plotW, yOf(0f)), 2f)
                }

                fun isNeg(p: Pt) = if (colorByValue) p.y < 0f else p.dir < 0
                // Split the series into same-coloured runs, cutting each run exactly where the
                // colour changes. A single segment that spans the change contributes part of its
                // length to each colour, so the line changes colour at the crossing itself.
                val runs = ArrayList<Pair<Boolean, ArrayList<Offset>>>()
                var neg = isNeg(pts[0])
                var run = arrayListOf(Offset(xOf(pts[0].t), yOf(pts[0].y)))
                for (k in 0 until pts.size - 1) {
                    val a = pts[k]
                    val b = pts[k + 1]
                    val nb = isNeg(b)
                    if (nb == neg) {
                        run += Offset(xOf(b.t), yOf(b.y))
                    } else {
                        val f = if (colorByValue) (a.y / (a.y - b.y)).coerceIn(0f, 1f) else 1f
                        val cut = Offset(
                            xOf(a.t) + (xOf(b.t) - xOf(a.t)) * f,
                            yOf(a.y + (b.y - a.y) * f),
                        )
                        run += cut
                        runs += neg to run
                        neg = nb
                        run = arrayListOf(cut, Offset(xOf(b.t), yOf(b.y)))
                    }
                }
                runs += neg to run
                runs.forEach { (n, ps) ->
                    // Skip single points and zero-length runs (a direction change at a single
                    // sample cuts at the same place, leaving nothing to draw).
                    if (ps.size < 2 || ps.all { it == ps[0] }) return@forEach
                    val path = Path()
                    path.moveTo(ps[0].x, ps[0].y)
                    for (q in ps.drop(1)) path.lineTo(q.x, q.y)
                    drawPath(path, if (n) amber else green, style = Stroke(width = strokePx))
                }

                selected?.let {
                    val x = xOf(it.t)
                    drawLine(labelColor, Offset(x, topPx), Offset(x, topPx + plotH), 2f)
                    drawCircle(Color.White, strokePx * 2.4f, Offset(x, yOf(it.y)))
                    drawCircle(if (isNeg(it)) amber else green, strokePx * 1.8f, Offset(x, yOf(it.y)))
                }

                val left = measurer.measure("−${(span / 60_000L).coerceAtLeast(1)}m", style)
                val nowLbl = measurer.measure("Now", style)
                if (!logTime) {
                    // The minute grid already labels the bottom edge, so only add endpoints when
                    // it left those spots free.
                    if (linearMarks.isEmpty()) {
                        drawText(left, topLeft = Offset(axisPx, size.height.toFloat() - left.size.height))
                        drawText(nowLbl, topLeft = Offset(axisPx + plotW - nowLbl.size.width, size.height.toFloat() - nowLbl.size.height))
                    }
                } else {
                    drawText(nowLbl, topLeft = Offset(axisPx + plotW - nowLbl.size.width, size.height.toFloat() - nowLbl.size.height))
                }
            }
        }
    }
}
