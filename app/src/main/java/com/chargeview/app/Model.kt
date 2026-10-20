package com.chargeview.app

import kotlin.math.abs
import kotlin.math.min

/** dir: 1 = charging, -1 = discharging, 0 = idle (full / not charging). */
data class Sample(
    val t: Long,
    val level: Float,
    val powerW: Float,
    val currentMa: Float,
    val tempC: Float,
    val dir: Int,
    val capacityMah: Float,
    val voltageV: Float = 0f,
) {
    fun toCsv() = "$t,$level,$powerW,$currentMa,$tempC,$dir,$capacityMah,$voltageV"

    companion object {
        fun fromCsv(line: String): Sample? = runCatching {
            val p = line.split(",")
            Sample(
                p[0].toLong(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat(),
                p[4].toFloat(), p[5].toInt(), p[6].toFloat(),
                p.getOrNull(7)?.toFloatOrNull() ?: 0f
            )
        }.getOrNull()
    }
}

enum class CardId(val key: String, val title: String) {
    LEVEL("level", "Battery charge"),
    POWER("power", "Battery power"),
    SOC("soc", "SoC change"),
    TEMP("temp", "Temperature"),
    VOLTAGE("voltage", "Battery voltage"),
    MAH("mah", "mAh (rate & total)"),
    TIME("time", "Time"),
    CHARGE_MAH("chargemah", "Charge moved (mAh)"),
    NEXT("next", "Next 1%"),
    RATE("rate", "Rate (% / hour)"),
    ENERGY("energy", "Energy (Wh)");

    companion object {
        val DEFAULT = listOf(LEVEL, POWER, SOC, TEMP)
        fun parse(s: String?): List<CardId> =
            s?.split(",")?.mapNotNull { k -> entries.firstOrNull { it.key == k } }?.distinct() ?: DEFAULT
    }
}

data class Session(
    val start: Long,
    val end: Long,
    val dir: Int,
    val startLevel: Float,
    val endLevel: Float,
    val peakPowerW: Float,
)

data class CapacityInfo(
    val designMah: Float?,
    val availableMah: Float?,
    val measuredMah: Float?,
    val effectiveMah: Float,
    val source: String,
)

data class Eta(val minutes: Float?, val low: Float?, val high: Float?, val label: String, val confidence: String)

object Estimator {
    private const val MIN = 60_000L
    private const val RATE_WINDOW = 20 * MIN
    private const val POWER_WINDOW = 5 * MIN
    private const val SESSION_GAP = 2 * MIN
    private const val DEFAULT_CAPACITY_MAH = 4000f

    /** Trailing samples since the last charge / discharge / idle event (direction change or recording gap). */
    fun run(samples: List<Sample>): List<Sample> {
        if (samples.isEmpty()) return emptyList()
        var i = samples.size - 1
        val dir = samples[i].dir
        while (i > 0 && samples[i - 1].dir == dir && samples[i].t - samples[i - 1].t <= SESSION_GAP) i--
        return samples.subList(i, samples.size)
    }

    /** SoC change over the trailing [minutes]; null until the current event is at least that old. */
    fun changeOver(run: List<Sample>, minutes: Int): Float? {
        val last = run.lastOrNull() ?: return null
        if (last.t - run.first().t < minutes * MIN) return null
        val ref = run.lastOrNull { it.t <= last.t - minutes * MIN } ?: return null
        return last.level - ref.level
    }

    /**
     * SoC change since the last charge / discharge event, growing in 1-minute steps up to a
     * rolling 10 minutes. Returns the change and the number of minutes it covers.
     */
    fun soc(run: List<Sample>): Pair<Float, Int>? {
        val last = run.lastOrNull() ?: return null
        val minutes = ((last.t - run.first().t) / MIN).toInt().coerceAtMost(10)
        if (minutes < 1) return null
        return (changeOver(run, minutes) ?: return null) to minutes
    }

    /** Smoothed signed rate in %/min and whether it came from measured SoC steps (vs. smoothed current). */
    fun rate(run: List<Sample>): Pair<Float, Boolean>? {
        val l = run.lastOrNull() ?: return null
        // SoC is a whole number, so the exact moments it steps are the most precise measurements.
        val steps = ArrayList<Sample>()
        var prev: Sample? = null
        for (s in run.filter { it.t >= l.t - RATE_WINDOW }) {
            if (prev != null && s.level != prev.level) steps += s
            prev = s
        }
        if (steps.size >= 2) {
            val dt = (steps.last().t - steps.first().t) / MIN.toFloat()
            if (dt >= 1f) return (steps.last().level - steps.first().level) / dt to true
        }
        val ma = run.filter { it.t >= l.t - POWER_WINDOW }.map { it.currentMa }
        if (ma.isEmpty()) return null
        val avgMa = ma.average().toFloat()
        if (avgMa == 0f) return null
        val cap = l.capacityMah.takeIf { it in 1500f..25000f } ?: DEFAULT_CAPACITY_MAH
        return (avgMa / cap * 100f / 60f) to false
    }

    fun capacityOf(s: Sample): Float = s.capacityMah.takeIf { it in 1500f..25000f } ?: DEFAULT_CAPACITY_MAH

    /** Times at which the whole-number SoC changed within [run]. */
    fun stepTimes(run: List<Sample>): List<Long> =
        run.zipWithNext().filter { (a, b) -> a.level != b.level }.map { it.second.t }

    /** Time to empty projected from recent discharging samples; used while charging, when no live discharge rate exists. */
    fun emptyFromHistory(samples: List<Sample>): Float? {
        val l = samples.lastOrNull() ?: return null
        val d = samples.takeLast(2000).filter { it.dir < 0 }.takeLast(60)
        if (d.size < 6) return null
        val cap = l.capacityMah.takeIf { it in 1500f..25000f } ?: DEFAULT_CAPACITY_MAH
        val ma = d.map { abs(it.currentMa) }.average().toFloat()
        if (ma < 1f) return null
        return l.level / (ma / cap * 100f / 60f)
    }

    // Charging slows above ~80%.
    private fun taper(level: Float): Float =
        if (level < 80f) 1f else (1f - 0.7f * (level - 80f) / 20f).coerceAtLeast(0.3f)

    fun eta(samples: List<Sample>, target: Int): Eta {
        val l = samples.lastOrNull() ?: return Eta(null, null, null, "Waiting for data", "")
        when (l.dir) {
            0 -> return Eta(
                null, null, null,
                if (l.level >= 99.5f) "Fully charged" else "Not charging", ""
            )
        }
        val measuring = Eta(null, null, null, "Measuring rate…", "")
        val (rate, measured) = rate(run(samples)) ?: return measuring
        val conf = if (measured) "Medium confidence" else "Low confidence"
        if (l.dir > 0) {
            if (l.level >= target) return Eta(0f, 0f, 0f, "At target", "")
            if (rate <= 0.01f) return Eta(null, null, null, "Charging very slowly", "")
            val base = rate / taper(l.level)
            var minutes = 0f
            var p = l.level
            while (p < target) {
                val step = min(1f, target - p)
                minutes += step / (base * taper(p + step / 2f))
                p += step
            }
            return Eta(minutes, minutes * 0.8f, minutes * 1.3f, "until $target%", conf)
        }
        if (rate >= -0.005f) return Eta(null, null, null, "Barely draining", "")
        val minutes = l.level / -rate
        return Eta(minutes, minutes * 0.7f, minutes * 1.3f, "until empty", conf)
    }
}

fun sessions(samples: List<Sample>): List<Session> {
    val out = mutableListOf<Session>()
    var cur = mutableListOf<Sample>()
    fun flush() {
        if (cur.size >= 2 && cur.first().dir != 0) {
            out += Session(
                cur.first().t, cur.last().t, cur.first().dir,
                cur.first().level, cur.last().level,
                cur.maxOf { abs(it.powerW) }
            )
        }
        cur = mutableListOf()
    }
    for (s in samples) {
        if (cur.isNotEmpty() && (s.dir != cur.last().dir || s.t - cur.last().t > 2 * 60_000L)) flush()
        cur += s
    }
    flush()
    return out.reversed()
}

fun fmtDuration(minutes: Float): String {
    val m = minutes.toInt()
    return when {
        m < 1 -> "<1 min"
        m < 60 -> "$m min"
        else -> "${m / 60} h ${m % 60} min"
    }
}

/** Integrates [v] (units per hour) over the samples since [fromT]; gaps over a minute are skipped. */
fun integrate(s: List<Sample>, fromT: Long, v: (Sample) -> Float): Float {
    var sum = 0f
    for (i in s.size - 1 downTo 1) {
        val b = s[i]
        val a = s[i - 1]
        if (b.t <= fromT) break
        if (b.t - a.t > 60_000L) continue
        sum += (v(a) + v(b)) / 2f * (b.t - maxOf(a.t, fromT)) / 3_600_000f
    }
    return sum
}

fun fmtSeconds(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}
