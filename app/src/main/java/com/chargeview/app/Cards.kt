package com.chargeview.app

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val WINDOW_MS = 30 * 60_000L
private val LARGE_H = 262.dp
private val GAP = 8.dp

class CardInfo(
    val title: String,
    val icon: String,
    val color: Color,
    val value: String,
    val unit: String,
    val note: String,
    val extras: List<Pair<String, String>>,
)

private fun avg(v: List<Float>) = if (v.isEmpty()) null else v.average().toFloat()

private fun f(v: Float?, fmt: String = "%.1f", unit: String = "") = if (v == null) "—" else fmt.format(v) + unit

private fun validCapacity(c: Float) = c in 1500f..25000f

@Composable
fun cardInfo(id: CardId, samples: List<Sample>, target: Int): CardInfo {
    val g = chargeGreen()
    val a = dischargeAmber()
    val last = samples.lastOrNull()
    val dc = if (last?.dir == -1) a else g
    val win = last?.let { l -> samples.takeLastWhile { it.t >= l.t - WINDOW_MS } } ?: emptyList()
    val run = Estimator.run(samples)
    val soc = Estimator.soc(run)
    val perFive = Estimator.changeOver(run, 5)
    val perTen = Estimator.changeOver(run, 10)
    val socChange = soc?.first
    fun c(v: Float?) = if (v != null && v < 0f) a else g
    fun pct(v: Float?) = v?.let { signed(it, 0) + " %" } ?: "—"
    fun signedPower(s: Sample) = abs(s.powerW) * (if (s.dir < 0) -1 else 1)
    val none = "—"
    val p10 = samples.takeLastWhile { last != null && it.t >= last.t - 10 * 60_000L }.map(::signedPower)
    return when (id) {
        CardId.LEVEL -> {
            val eta = glidedEta(samples, target)
            val note = when {
                eta.minutes != null && eta.minutes > 0f ->
                    "~${fmtDuration(eta.minutes)} " + if (last?.dir == 1) "to target" else "to empty"
                else -> eta.label
            }
            CardInfo(id.title, "▮", dc, last?.level?.toInt()?.toString() ?: none, "%", note, emptyList())
        }
        CardId.POWER -> CardInfo(
            id.title, "ϟ", dc,
            last?.let { signed(signedPower(it)) } ?: none, "W", "+ charge · − discharge",
            listOf(
                "Charge avg (10 min)" to f(avg(p10.filter { it > 0f }), "%+.1f", " W"),
                "Charge peak (30 min)" to f(win.map(::signedPower).filter { it > 0f }.maxOrNull(), "%+.1f", " W"),
                "Discharge avg (10 min)" to f(avg(p10.filter { it < 0f }), "%+.1f", " W"),
                "Discharge peak (30 min)" to f(win.map(::signedPower).filter { it < 0f }.minOrNull(), "%+.1f", " W"),
            ),
        )
        CardId.SOC -> CardInfo(
            id.title, if ((socChange ?: 0f) < 0f) "↘" else "↗", c(socChange),
            socChange?.let { signed(it, 0) } ?: none, "% · ${soc?.second ?: 0}m",
            when {
                soc == null -> "Needs 1 min of data"
                soc.second < 10 -> "Since " + if (last?.dir == -1) "unplugged" else if (last?.dir == 1) "plugged in" else "last event"
                else -> "Rolling 10 minutes"
            },
            listOf(
                "1 min" to pct(Estimator.changeOver(run, 1)),
                "5 min" to pct(perFive),
                "10 min" to pct(perTen),
                "Equivalent" to (Estimator.rate(run)?.first?.let { signed(it * 60f, 1) + " %/h" } ?: none),
            ),
        )
        CardId.TEMP -> {
            val t = win.map { it.tempC }
            CardInfo(
                id.title, "◉", a,
                last?.let { "%.1f".format(it.tempC) } ?: none, "°C", "Battery temperature",
                listOf(
                    "30 min min" to f(t.minOrNull(), "%.1f", " °C"),
                    "30 min avg" to f(avg(t), "%.1f", " °C"),
                    "30 min max" to f(t.maxOrNull(), "%.1f", " °C"),
                ),
            )
        }
        CardId.VOLTAGE -> {
            val v = win.map { it.voltageV }.filter { it > 0f }
            CardInfo(
                id.title, "⚡", g,
                last?.takeIf { it.voltageV > 0f }?.let { "%.2f".format(it.voltageV) } ?: none, "V", "Battery terminal voltage",
                listOf(
                    "30 min min" to f(v.minOrNull(), "%.2f", " V"),
                    "30 min avg" to f(avg(v), "%.2f", " V"),
                    "30 min max" to f(v.maxOrNull(), "%.2f", " V"),
                ),
            )
        }
        CardId.TIME -> {
            val eta = glidedEta(samples, target)
            val charging = last?.dir == 1
            val toTarget = if (charging) eta.minutes else null
            val toEmpty = when (last?.dir) {
                -1 -> eta.minutes
                else -> Estimator.emptyFromHistory(samples)
            }
            val main = if (charging) toTarget else toEmpty
            val sinceMin = run.takeIf { it.size > 1 }?.let { (it.last().t - it.first().t) / 60_000f }
            val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            fun at(m: Float?) = if (m == null || last == null) none else clock.format(java.util.Date(last.t + (m * 60_000f).toLong()))
            val state = when (last?.dir) { 1 -> "Charging"; -1 -> "Discharging"; else -> "Not charging" }
            CardInfo(
                id.title, "⏱", dc,
                main?.let { if (it < 1f) "<1m" else fmtDuration(it).replace(" min", "m").replace(" h ", "h ") } ?: none,
                "",
                if (charging) "to $target%" else if (last?.dir == -1) "to empty" else eta.label,
                listOf(
                    "To $target%" to (toTarget?.let(::fmtDuration) ?: none),
                    "To empty" to (toEmpty?.let { fmtDuration(it) + if (last?.dir == -1) "" else " (at usage)" } ?: none),
                    "Reaches target" to at(toTarget),
                    "$state for" to (sinceMin?.let(::fmtDuration) ?: none),
                ),
            )
        }
        CardId.CHARGE_MAH -> {
            val ma = { s: Sample -> s.currentMa }
            val m1 = last?.let { integrate(win, it.t - 60_000L, ma) }
            val cap = last?.let { Estimator.capacityOf(it) } ?: 4000f
            val ev = run.takeIf { it.size > 1 }?.let { integrate(it, it.first().t, ma) }
            CardInfo(
                id.title, "◔", c(m1),
                m1?.let { signed(it, 0) } ?: none, "mAh", "Last 60 seconds",
                listOf(
                    "5 min" to (last?.let { signed(integrate(win, it.t - 5 * 60_000L, ma), 0) + " mAh" } ?: none),
                    "10 min" to (last?.let { signed(integrate(win, it.t - 10 * 60_000L, ma), 0) + " mAh" } ?: none),
                    "This event" to (ev?.let { signed(it, 0) + " mAh" } ?: none),
                    "Event, % of capacity" to (ev?.let { signed(it / cap * 100f, 2) + " %" } ?: none),
                ),
            )
        }
        CardId.MAHMIN -> {
            val ma = { s: Sample -> s.currentMa }
            val cap = last?.let { Estimator.capacityOf(it) } ?: 4000f
            // Average amps over a trailing window, which is the charge slope in mAh/min.
            fun slope(span: Long): Float? {
                val l = last ?: return null
                val m = win.filter { it.t >= l.t - span }
                if (m.size < 2) return null
                return m.map(ma).average().toFloat() / 60f
            }
            val v = slope(60_000L)
            val rate = v?.let { it * 60f / cap * 100f }
            val peakSeries = seriesFor(CardId.MAHMIN, samples).pts.map { it.y }
            val peak = if (last?.dir == -1) peakSeries.minOrNull() else peakSeries.maxOrNull()
            CardInfo(
                id.title, "⇄", c(v),
                v?.let { signed(it, 1) } ?: none, "mAh/min", "Last 60 seconds",
                listOf(
                    "5 min" to (slope(5 * 60_000L)?.let { signed(it, 1) + " mAh/min" } ?: none),
                    "10 min" to (slope(10 * 60_000L)?.let { signed(it, 1) + " mAh/min" } ?: none),
                    "Equivalent" to (rate?.let { signed(it, 0) + " %/h" } ?: none),
                    "Peak (30 min)" to (peak?.let { signed(it, 1) + " mAh/min" } ?: none),
                ),
            )
        }
        CardId.NEXT -> {
            val steps = Estimator.stepTimes(run)
            val since = last?.let { (it.t - (steps.lastOrNull() ?: run.first().t)) / 1000f }
            val rate = if (last == null || last.dir == 0) null else Estimator.rate(run)?.first?.takeIf { abs(it) > 0.005f }
            val expected = rate?.let { 60f / abs(it) }
            val durs = steps.zipWithNext { x, y -> (y - x) / 1000f }.takeLast(5)
            val remaining = if (expected != null && since != null) (expected - since).coerceAtLeast(0f) else null
            CardInfo(
                id.title, "◴", dc,
                remaining?.let { if (it < 10f) "<10s" else "~" + fmtSeconds(it) } ?: none, "",
                since?.let { "Last step ${fmtSeconds(it)} ago" } ?: "Waiting for data",
                listOf(
                    "Since last step" to (since?.let { fmtSeconds(it) } ?: none),
                    "Expected per 1%" to (expected?.let { fmtSeconds(it) } ?: none),
                    "Avg of last ${durs.size.coerceAtLeast(1)}" to (durs.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.let { fmtSeconds(it) } ?: none),
                    "Recent steps" to (durs.takeIf { it.isNotEmpty() }?.takeLast(3)?.joinToString(" · ") { fmtSeconds(it) } ?: none),
                ),
            )
        }
        CardId.RATE -> {
            val cap = last?.let { Estimator.capacityOf(it) } ?: 4000f
            fun perHour(span: Long): Float? {
                val l = last ?: return null
                val m = win.filter { it.t >= l.t - span }.map { it.currentMa }
                return if (m.isEmpty()) null else m.average().toFloat() / cap * 100f
            }
            val r = Estimator.rate(run)?.takeIf { last?.dir != 0 }
            val v = r?.first?.times(60f) ?: perHour(5 * 60_000L)
            val peakSeries = seriesFor(CardId.RATE, samples).pts.map { it.y }
            val peak = if (last?.dir == -1) peakSeries.minOrNull() else peakSeries.maxOrNull()
            CardInfo(
                id.title, "↗", c(v),
                v?.let { signed(it, 1) } ?: none, "%/h",
                if (r == null) "Measuring rate…" else if (r.second) "Now, from SoC steps" else "Now, from smoothed current",
                listOf(
                    "Now" to (v?.let { signed(it, 1) + " %/h" } ?: none),
                    "1 min avg" to (perHour(60_000L)?.let { signed(it, 1) + " %/h" } ?: none),
                    "5 min avg" to (perHour(5 * 60_000L)?.let { signed(it, 1) + " %/h" } ?: none),
                    "10 min avg" to (perHour(10 * 60_000L)?.let { signed(it, 1) + " %/h" } ?: none),
                    "Peak (30 min)" to (peak?.let { signed(it, 1) + " %/h" } ?: none),
                ),
            )
        }
        CardId.ENERGY -> {
            val w = { s: Sample -> s.powerW }
            val m1 = last?.let { integrate(win, it.t - 60_000L, w) }
            val ev = run.takeIf { it.size > 1 }?.let { integrate(it, it.first().t, w) }
            val hrs = run.takeIf { it.size > 1 }?.let { (it.last().t - it.first().t) / 3_600_000f }
            CardInfo(
                id.title, "Wh", c(m1),
                m1?.let { signed(it, 2) } ?: none, "Wh", "Last 60 seconds",
                listOf(
                    "5 min" to (last?.let { signed(integrate(win, it.t - 5 * 60_000L, w), 2) + " Wh" } ?: none),
                    "10 min" to (last?.let { signed(integrate(win, it.t - 10 * 60_000L, w), 2) + " Wh" } ?: none),
                    "This event" to (ev?.let { signed(it, 2) + " Wh" } ?: none),
                    "Event avg power" to (if (ev != null && hrs != null && hrs > 0f) signed(ev / hrs, 1) + " W" else none),
                ),
            )
        }
        CardId.MAH -> {
            val s = last
            val cap = s?.capacityMah?.takeIf(::validCapacity)
            CardInfo(
                id.title, "▤", dc,
                s?.let { signed(it.currentMa, 0) } ?: none, "mA",
                if (cap != null) "of ~%.0f mAh total".format(cap) else "Total not available",
                listOf(
                    "Total" to (cap?.let { "~%.0f mAh".format(it) } ?: none),
                    "In battery" to (if (s == null || cap == null) none else "~%.0f mAh".format(cap * s.level / 100f)),
                    "Rate" to (if (s == null || cap == null) none else signed(s.currentMa / cap * 100f, 1) + " %/h"),
                ),
            )
        }
    }
}

/** Series plotted in the large graph card when a card is selected. */
class Series(
    val pts: List<Pt>,
    val unit: String,
    val zeroLine: Boolean,
    val fixed: ClosedFloatingPointRange<Float>?,
    val integer: Boolean = false,
)

/** SoC change at each whole minute: since the last event, growing to a rolling 10 minutes (or a fixed window). */
private fun socSeries(run: List<Sample>, windowMin: Int, fixedMinutes: Int?): List<Pt> {
    val last = run.lastOrNull() ?: return emptyList()
    val out = mutableListOf<Pt>()
    for (k in windowMin downTo 0) {
        val tp = last.t - k * 60_000L
        val upto = run.takeWhile { it.t <= tp }
        if (upto.size < 2) continue
        val cur = upto.last()
        val m = fixedMinutes ?: ((cur.t - upto.first().t) / 60_000L).toInt().coerceAtMost(10)
        if (m < 1 || cur.t - upto.first().t < m * 60_000L) continue
        val ref = upto.lastOrNull { it.t <= cur.t - m * 60_000L } ?: continue
        out += Pt(cur.t, cur.level - ref.level, cur.dir)
    }
    return out
}

/** Amount moved over the trailing minute at each sample, for a smooth fine-grained trend. */
private fun rolling(win: List<Sample>, v: (Sample) -> Float): List<Pt> =
    win.indices.map { i ->
        val s = win[i]
        Pt(s.t, integrate(win.subList(maxOf(0, i - 13), i + 1), s.t - 60_000L, v), s.dir)
    }

fun seriesFor(id: CardId, samples: List<Sample>, windowMs: Long = WINDOW_MS): Series {
    val last = samples.lastOrNull()?.t ?: 0L
    val from = last - windowMs
    val win = samples.takeLastWhile { it.t >= from }
    val min = (windowMs / 60_000L).toInt()
    return when (id) {
        CardId.LEVEL -> Series(win.map { Pt(it.t, it.level, it.dir) }, "%", false, 0f..100f, true)
        CardId.POWER -> Series(win.map { Pt(it.t, if (it.dir < 0) -abs(it.powerW) else abs(it.powerW), it.dir) }, "W", true, null)
        CardId.TEMP -> Series(win.map { Pt(it.t, it.tempC, it.dir) }, "°C", false, null)
        CardId.SOC -> Series(socSeries(Estimator.run(samples), min, null), "%", true, null, true)
            CardId.VOLTAGE -> Series(win.filter { it.voltageV > 0f }.map { Pt(it.t, it.voltageV, it.dir) }, "V", false, null)
        CardId.MAH -> Series(win.map { Pt(it.t, if (it.dir < 0) -abs(it.currentMa) else abs(it.currentMa), it.dir) }, "mA", true, null)
        CardId.MAHMIN -> Series(win.map { Pt(it.t, it.currentMa / 60f, it.dir) }, "mAh/min", true, null)
        CardId.TIME -> Series(win.map { Pt(it.t, it.level, it.dir) }, "%", false, 0f..100f, true)
        CardId.CHARGE_MAH -> Series(rolling(win) { it.currentMa }, "mAh", true, null)
        CardId.ENERGY -> Series(rolling(win) { it.powerW }, "Wh", true, null)
        CardId.RATE -> {
            val cap = samples.lastOrNull()?.let { Estimator.capacityOf(it) } ?: 4000f
            Series(win.indices.map { i ->
                val s = win[i]
                val m = win.subList(maxOf(0, i - 12), i + 1).map { it.currentMa }.average().toFloat()
                Pt(s.t, m / cap * 100f, s.dir)
            }, "%/h", true, null)
        }
        CardId.NEXT -> {
            val run = Estimator.run(samples)
            val steps = Estimator.stepTimes(run).filter { it >= from }
            val dir = samples.lastOrNull()?.dir ?: 0
            Series(steps.zipWithNext { a, b -> Pt(b, (b - a) / 60_000f, dir) }, "min", false, null)
        }
    }
}

/** Tiny last-10-minutes trend line for a small card. */
@Composable
private fun Sparkline(pts: List<Pt>, endT: Long, modifier: Modifier) {
    val g = chargeGreen()
    val a = dischargeAmber()
    val base = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        // Half the large graph's stroke.
        val sparkStroke = 1.dp.toPx()
        val t0 = endT - 10 * 60_000L
        val pad = 3.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        if (pts.size < 2) {
            drawLine(base, Offset(pad, size.height / 2), Offset(size.width - pad, size.height / 2), 2f)
            return@Canvas
        }
        var lo = pts.minOf { it.y }
        var hi = pts.maxOf { it.y }
        if (hi - lo < 1e-3f) { lo -= 0.5f; hi += 0.5f }
        fun x(t: Long) = pad + (t - t0).toFloat() / (endT - t0) * w
        fun y(v: Float) = pad + (1f - (v - lo) / (hi - lo)) * h
        for (i in 0 until pts.size - 1) {
            // Value-based colouring, matching the large graph: the sign decides the colour, and
            // sign changes are split at the zero crossing.
            val a0 = pts[i]
            val b0 = pts[i + 1]
            val na = a0.y < 0f
            val nb = b0.y < 0f
            if (na == nb) {
                drawLine(
                    if (na) a else g,
                    Offset(x(a0.t), y(a0.y)), Offset(x(b0.t), y(b0.y)),
                    strokeWidth = sparkStroke, cap = StrokeCap.Round,
                )
            } else {
                val f = (a0.y / (a0.y - b0.y)).coerceIn(0f, 1f)
                val mx = x(a0.t) + (x(b0.t) - x(a0.t)) * f
                drawLine(
                    if (na) a else g,
                    Offset(x(a0.t), y(a0.y)), Offset(mx, y(0f)),
                    strokeWidth = sparkStroke, cap = StrokeCap.Round,
                )
                drawLine(
                    if (nb) a else g,
                    Offset(mx, y(0f)), Offset(x(b0.t), y(b0.y)),
                    strokeWidth = sparkStroke, cap = StrokeCap.Round,
                )
            }
        }
        val p = pts.last()
        drawCircle(if (p.y < 0f) a else g, 2.5.dp.toPx(), Offset(x(p.t), y(p.y)))
    }
}

@Composable
private fun SmallCard(info: CardInfo, trend: Series, endT: Long, selected: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 72.dp
        SmallCardBody(info, trend, endT, selected, compact, Modifier.fillMaxSize())
    }
}

@Composable
private fun SmallCardBody(info: CardInfo, trend: Series, endT: Long, selected: Boolean, compact: Boolean, modifier: Modifier) {
    Panel(modifier, borderColor = if (selected) info.color else MaterialTheme.colorScheme.outlineVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center) {
            Row {
                Text(info.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                Text(info.icon, color = info.color, fontSize = 16.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) { append(info.value) }
                        if (info.value != "—") withStyle(SpanStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" ${info.unit}") }
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Sparkline(trend.pts, endT, Modifier.width(52.dp).height(if (compact) 26.dp else 28.dp))
            }
            if (!compact) Text(info.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun LargeCard(info: CardInfo, selected: Boolean, modifier: Modifier) {
    val brush = Brush.linearGradient(
        if (LocalDark.current) listOf(Color(0xFF1A2B21), Color(0xFF111815))
        else listOf(Color(0xFFE3F4EA), Color(0xFFFFFFFF))
    )
    Panel(modifier, borderColor = info.color.copy(alpha = if (selected) 1f else 0.35f), brush = brush) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(info.icon, color = info.color, fontSize = 20.sp)
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold)) { append(info.value) }
                    if (info.value != "—") withStyle(SpanStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" ${info.unit}") }
                },
                maxLines = 1,
            )
            Text(info.note, fontSize = 13.sp, color = info.color)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            info.extras.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** First card is large, the rest fill a 2-column grid. Long-press a card and drag to move it. */
@Composable
fun CardGrid(
    cards: List<CardId>,
    samples: List<Sample>,
    target: Int,
    selected: CardId,
    onSelect: (CardId) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    display: List<Sample> = samples,
    scroll: ScrollState? = null,
) {
    if (cards.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("No cards shown. Turn some on in Settings › Home screen cards.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        return
    }
    val last = samples.lastOrNull()
    BoxWithConstraints(modifier) {
        val d = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val smalls = cards.size - 1
        val rows = (smalls + 1) / 2
        val smallH = if (rows == 0) 80.dp
        else ((maxHeight - LARGE_H - GAP - GAP * (rows - 1)) / rows).coerceIn(60.dp, 120.dp)
        val wPx = with(d) { maxWidth.toPx() }
        val gapPx = with(d) { GAP.toPx() }
        val largePx = with(d) { LARGE_H.toPx() }
        val smallPx = with(d) { smallH.toPx() }
        val colPx = (wPx - gapPx) / 2f
        val rectOf = { i: Int ->
            if (i == 0) Rect(Offset.Zero, Size(wPx, largePx))
            else {
                val k = i - 1
                Rect(Offset((k % 2) * (colPx + gapPx), largePx + gapPx + (k / 2) * (smallPx + gapPx)), Size(colPx, smallPx))
            }
        }
        var dragId by remember { mutableStateOf<CardId?>(null) }
        var drag by remember { mutableStateOf(Offset.Zero) }
        val rectNow by rememberUpdatedState(rectOf)
        val cardsNow by rememberUpdatedState(cards)
        val moveNow by rememberUpdatedState(onMove)
        val selectNow by rememberUpdatedState(onSelect)

        cards.forEachIndexed { i, id ->
            key(id) {
                val idx by rememberUpdatedState(i)
                val r = rectOf(i)
                val dragging = dragId == id
                Box(
                    Modifier
                        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
                        .zIndex(if (dragging) 3f else if (i == 0) 2f else 0f)
                        .then(if (i == 0 && scroll != null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
                        .graphicsLayer {
                            if (i == 0 && scroll != null) translationY = scroll.value.toFloat()
                            if (dragging) {
                                translationX = drag.x
                                translationY += drag.y
                                scaleX = 1.03f
                                scaleY = 1.03f
                                alpha = 0.92f
                            }
                        }
                        .pointerInput(id) { detectTapGestures(onTap = { selectNow(id) }) }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragId = id
                                    drag = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount
                                },
                                onDragEnd = {
                                    val center = rectNow(idx).center + drag
                                    val to = cardsNow.indices.minByOrNull { (rectNow(it).center - center).getDistance() } ?: idx
                                    dragId = null
                                    drag = Offset.Zero
                                    moveNow(idx, to)
                                },
                                onDragCancel = {
                                    dragId = null
                                    drag = Offset.Zero
                                },
                            )
                        }
                ) {
                    val sel = id == selected
                    if (i == 0 && id == CardId.LEVEL) {
                        Hero(display, last, target, sel, Modifier.fillMaxSize())
                    } else if (i == 0) {
                        LargeCard(cardInfo(id, display, target), sel, Modifier.fillMaxSize())
                    } else {
                        SmallCard(
                            cardInfo(id, display, target),
                            seriesFor(id, samples, 10 * 60_000L).let { s -> Series(s.pts, s.unit, s.zeroLine, null) },
                            last?.t ?: 0L, sel, Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

class Glide(val samples: List<Sample>, val progress: Float, val real: List<Sample> = samples)

val LocalGlide = compositionLocalOf<Glide?> { null }

/** Time estimate that glides from the previous reading's estimate to the latest one while smoothing is active. */
@Composable
fun glidedEta(samples: List<Sample>, target: Int): Eta {
    val g = LocalGlide.current
    val p = g?.progress ?: 1f
    val real = g?.real ?: samples
    val now = Estimator.eta(real, target)
    if (p >= 1f || real.size < 3) return now
    val prev = Estimator.eta(real.subList(0, real.size - 1), target)
    val a = prev.minutes ?: return now
    val b = now.minutes ?: return now
    fun l(x: Float, y: Float) = x + (y - x) * p
    return Eta(l(a, b), l(prev.low ?: a, now.low ?: b), l(prev.high ?: a, now.high ?: b), now.label, now.confidence)
}

/**
 * Display-only smoothing: the card values glide linearly from the previous reading to the latest one
 * (every [updateMs], at most 1 s), so they lag one polling interval behind and never extrapolate.
 */
@Composable
fun rememberGlided(samples: List<Sample>, updateMs: Int): Glide {
    val pollMs = updateMs.coerceAtLeast(1000).toLong()
    val tickMs = updateMs.coerceAtMost(1000).toLong()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tickMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(tickMs)
        }
    }
    if (samples.size < 2) return Glide(samples, 1f)
    val b = samples.last()
    val a = samples[samples.size - 2]
    val gap = b.t - a.t
    if (gap <= 0L || gap > pollMs * 5 / 2) return Glide(samples, 1f)
    val p = ((now - b.t).toFloat() / gap).coerceIn(0f, 1f)
    if (p >= 1f) return Glide(samples, 1f)
    fun l(x: Float, y: Float) = x + (y - x) * p
    val v = b.copy(
        t = a.t + (gap * p).toLong(),
        powerW = l(a.powerW, b.powerW),
        currentMa = l(a.currentMa, b.currentMa),
    )
    return Glide(samples.subList(0, samples.size - 1) + v, p, samples)
}

@Composable
fun HomeScreen(vm: BatteryViewModel, samples: List<Sample>, target: Int, recording: Boolean) {
    val cards by vm.cards.collectAsState()
    val updateMs by vm.updateMs.collectAsState()
    val glide = rememberGlided(samples, updateMs)
    val display = glide.samples
    var selectedKey by rememberSaveable { mutableStateOf(CardId.POWER.key) }
    val chosen = CardId.entries.firstOrNull { it.key == selectedKey }
    val selected = chosen?.takeIf { it in cards } ?: cards.firstOrNull() ?: CardId.POWER
    val last = samples.lastOrNull()
    val dir = last?.dir ?: 0
    val smalls = (cards.size - 1).coerceAtLeast(0)
    val rowH = 80.dp
    val scroll = rememberScrollState()
    val gridH = if (cards.isEmpty()) 60.dp else LARGE_H + (if (smalls > 0) (GAP + rowH) * ((smalls + 1) / 2) else 0.dp)

    CompositionLocalProvider(LocalGlide provides glide) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (dir) { 1 -> "LIVE CHARGING"; -1 -> "ON BATTERY"; else -> "NOT CHARGING" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(8.dp).clip(CircleShape).background(chargeGreen()))
                Spacer(Modifier.width(6.dp))
                Text(if (last == null) "Waiting…" else "Updated now", color = chargeGreen(), fontSize = 13.sp)
            }
            val onSelect = { id: CardId -> selectedKey = id.key }
            if (wide) {
                Row(Modifier.weight(1f).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).fillMaxSize().verticalScroll(scroll)) {
                        CardGrid(cards, samples, target, selected, onSelect, vm::moveCard, Modifier.fillMaxWidth().height(gridH), display, scroll)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChartCard(selected, samples, target, Modifier.weight(1f))
                        RecordingCard(samples, recording)
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(scroll).padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CardGrid(cards, samples, target, selected, onSelect, vm::moveCard, Modifier.fillMaxWidth().height(gridH), display, scroll)
                    ChartCard(selected, samples, target, Modifier.fillMaxWidth().height(320.dp))
                    RecordingCard(samples, recording)
                }
            }
        }
    }
    }
}

@Composable
private fun ChartCard(id: CardId, samples: List<Sample>, target: Int, modifier: Modifier) {
    val series = remember(id, samples) { seriesFor(id, samples) }
    var logTime by rememberSaveable { mutableStateOf(true) }
    Panel(modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                RangeChip(if (logTime) "Log time" else "Linear time", true) { logTime = !logTime }
            }
            if (id == CardId.TIME || id == CardId.LEVEL) {
                val eta = glidedEta(samples, target)
                val last = samples.lastOrNull()
                val charging = last?.dir == 1
                val note = when {
                    eta.minutes == null || eta.minutes <= 0f -> eta.label
                    charging -> "~${fmtDuration(eta.minutes)} until $target% · reaches ${
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date((last?.t ?: 0L) + (eta.minutes * 60_000f).toLong()))
                    }"
                    else -> "~${fmtDuration(eta.minutes)} until empty"
                }
                if (note.isNotBlank()) {
                    Text(
                        note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            LineChart(
                series.pts, series.unit,
                zeroLine = series.zeroLine,
                fixedRange = series.fixed,
                integer = series.integer,
                logTime = logTime,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                // On the graphs that cross zero the colour follows the sign of the value, so the
                // legend has to describe that rather than charging and discharging state.
                val byValue = series.zeroLine && series.fixed == null &&
                    series.pts.any { it.y < 0f } && series.pts.any { it.y > 0f }
                if (byValue) {
                    LegendDot(chargeGreen(), "Above 0 (+)")
                    LegendDot(dischargeAmber(), "Below 0 (−)")
                } else {
                    LegendDot(chargeGreen(), "Charging (+)")
                    LegendDot(dischargeAmber(), "Discharging (−)")
                }
            }
        }
    }
}

private fun smallDesc(id: CardId) = when (id) {
    CardId.LEVEL -> "Charge % and time to target / empty"
    CardId.POWER -> "Watts in / out (+ charge, − discharge)"
    CardId.SOC -> "% moved since plug / unplug (1–10 min)"
    CardId.TEMP -> "Battery temperature in °C"
    CardId.VOLTAGE -> "Battery voltage in V"
    CardId.MAH -> "Current in mA, plus total capacity in mAh"
    CardId.MAHMIN -> "Charge slope in mAh per minute"
    CardId.TIME -> "Time to charge target, or to empty when discharging"
    CardId.CHARGE_MAH -> "mAh moved in or out over the last minute"
    CardId.NEXT -> "Countdown to the next 1% step"
    CardId.RATE -> "Instant speed in % per hour"
    CardId.ENERGY -> "Watt-hours moved over the last minute"
}

private fun largeDesc(id: CardId) = when (id) {
    CardId.LEVEL -> "adds progress bar, target, ETA, confidence"
    CardId.POWER -> "adds charge and discharge average and peak"
    CardId.SOC -> "adds 1 / 5 / 10 min movement and the implied %/h"
    CardId.TEMP -> "adds 30 min min / avg / max"
    CardId.VOLTAGE -> "adds 30 min min / avg / max"
    CardId.MAH -> "adds total, in battery, % per hour"
    CardId.MAHMIN -> "adds 5 / 10 min average, implied %/h and 30 min peak"
    CardId.TIME -> "adds to target / empty / 100%, time since plug or unplug, finish clock time"
    CardId.CHARGE_MAH -> "adds 5 / 10 min, this event, % of capacity"
    CardId.NEXT -> "adds time since last step, expected per 1%, recent steps"
    CardId.RATE -> "adds now, 1 / 5 / 10 min averages and 30 min peak"
    CardId.ENERGY -> "adds 5 / 10 min, this event, average power"
}

@Composable
fun CardsSettingsScreen(vm: BatteryViewModel, samples: List<Sample>, back: () -> Unit) {
    val cards by vm.cards.collectAsState()
    val target by vm.target.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.padding(top = 6.dp).clickable(onClick = back).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹ ", color = chargeGreen(), fontSize = 20.sp)
            Text("Home screen cards", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Text(
            "Hold and drag a card on Home to move it. First card is large; tap any card to graph it. Up to ${BatteryViewModel.MAX_CARDS} cards.",
            fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                CardId.entries.forEachIndexed { n, id ->
                    if (n > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val info = cardInfo(id, samples, target)
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(id.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                                Text(
                                    "  now ${info.value}${if (info.value != "—") " " + info.unit else ""}",
                                    fontSize = 12.sp, color = info.color, maxLines = 1,
                                )
                            }
                            Text("Small: ${smallDesc(id)}", fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Large: ${largeDesc(id)}", fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(id in cards, { vm.setCardShown(id, it) }, enabled = id in cards || cards.size < BatteryViewModel.MAX_CARDS)
                    }
                }
            }
        }
    }
}
