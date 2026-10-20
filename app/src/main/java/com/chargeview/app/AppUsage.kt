package com.chargeview.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import java.io.File

data class AppUse(val label: String, val foregroundMs: Long, val share: Double)

data class SystemLoad(
    val cpuFreqPct: Double?,
    val thermalStatus: String?,
    val thermalHeadroom: Double?,
)

object AppUsage {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // unsafeCheckOpNoThrow only exists from API 29; minSdk is 26.
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Foreground time per app over the last [windowMs], largest first; share is of all listed apps. */
    fun top(ctx: Context, windowMs: Long, limit: Int = 5): List<AppUse> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Access can be revoked between the check and the query, and the platform may return null.
        val stats = runCatching { usm.queryAndAggregateUsageStats(now - windowMs, now) }.getOrNull() ?: return emptyList()
        val pm = ctx.packageManager
        val rows = stats.values
            .filter { it.totalTimeInForeground > 0 && it.packageName != ctx.packageName }
            .mapNotNull {
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString()
                } catch (e: Exception) {
                    null
                }
                label?.let { l -> l to it.totalTimeInForeground }
            }
        val total = rows.sumOf { it.second }.toDouble()
        if (total <= 0) return emptyList()
        return rows.sortedByDescending { it.second }.take(limit).map { AppUse(it.first, it.second, it.second / total) }
    }

    /** CPU clock relative to each core's maximum; null when the kernel files are not readable. */
    private fun cpuFreqPct(): Double? {
        val ratios = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { i ->
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readLong(base + "scaling_cur_freq")
            val max = readLong(base + "cpuinfo_max_freq") ?: readLong(base + "scaling_max_freq")
            if (cur != null && max != null && max > 0) cur.toDouble() / max else null
        }
        return if (ratios.isEmpty()) null else ratios.average() * 100
    }

    private fun readLong(path: String): Long? = try {
        File(path).readText().trim().toLong()
    } catch (e: Exception) {
        null
    }

    fun systemLoad(ctx: Context): SystemLoad {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = if (Build.VERSION.SDK_INT >= 29) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                else -> "Critical"
            }
        } else null
        val headroom = if (Build.VERSION.SDK_INT >= 30) {
            pm.getThermalHeadroom(10).takeIf { !it.isNaN() }?.toDouble()
        } else null
        return SystemLoad(cpuFreqPct(), status, headroom)
    }
}
