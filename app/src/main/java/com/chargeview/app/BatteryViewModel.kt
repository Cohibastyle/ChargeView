package com.chargeview.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class BatteryViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val prefs = ctx.getSharedPreferences("chargeview", Context.MODE_PRIVATE)
    private val file = File(ctx.filesDir, "history.csv")
    private val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val _samples = MutableStateFlow<List<Sample>>(emptyList())
    val samples: StateFlow<List<Sample>> = _samples

    private val _userCapacity = MutableStateFlow(prefs.getInt("capacity", 0))
    val userCapacity: StateFlow<Int> = _userCapacity

    private val _capacity = MutableStateFlow(CapacityInfo(null, null, null, DEFAULT_MAH, "Default (not reported by this phone)"))
    val capacity: StateFlow<CapacityInfo> = _capacity

    // Design capacity from Android's internal power profile; not available on every device.
    private val designMah: Float? by lazy {
        runCatching {
            val c = Class.forName("com.android.internal.os.PowerProfile")
            val p = c.getConstructor(Context::class.java).newInstance(ctx)
            (c.getMethod("getBatteryCapacity").invoke(p) as Double).toFloat()
        }.getOrNull()?.takeIf { it in VALID_MAH }
    }

    fun setUserCapacity(v: Int) {
        _userCapacity.value = v
        prefs.edit().putInt("capacity", v).apply()
    }

    /** Display update interval. Real readings are polled no faster than 1 s; shorter values are smoothed. */
    private val _updateMs = MutableStateFlow(prefs.getInt("update_ms", DEFAULT_UPDATE_MS))
    val updateMs: StateFlow<Int> = _updateMs

    fun setUpdateMs(v: Int) {
        _updateMs.value = v
        prefs.edit().putInt("update_ms", v).apply()
    }

    private val _theme = MutableStateFlow(prefs.getString("theme", "dark") ?: "dark")
    val theme: StateFlow<String> = _theme

    private val _target = MutableStateFlow(prefs.getInt("target", 100))
    val target: StateFlow<Int> = _target

    private val _recording = MutableStateFlow(prefs.getBoolean("record", true))
    val recording: StateFlow<Boolean> = _recording

    init {
        viewModelScope.launch {
            _samples.value = withContext(Dispatchers.IO) { load() }
            while (true) {
                read()?.let { s ->
                    _samples.value = (_samples.value + s).takeLast(MAX_SAMPLES)
                    if (_recording.value) withContext(Dispatchers.IO) { file.appendText(s.toCsv() + "\n") }
                }
                delay(_updateMs.value.coerceAtLeast(500).toLong())
            }
        }
    }

    private fun load(): List<Sample> {
        if (!file.exists()) return emptyList()
        val all = file.readLines().mapNotNull { Sample.fromCsv(it) }.takeLast(MAX_SAMPLES)
        file.writeText(all.joinToString("") { it.toCsv() + "\n" })
        return all
    }

    private fun read(): Sample? {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val pct = level * 100f / scale
        val dir = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> 1
            BatteryManager.BATTERY_STATUS_DISCHARGING -> -1
            else -> 0
        }
        val volts = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        // Devices disagree on the sign of CURRENT_NOW, so derive the sign from the charge status.
        val ma = abs(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) / 1000f * dir
        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val availableMah = if (counter > 0) counter / 1000f else null
        // Charge counter / SoC is only meaningful once there is enough charge to divide by.
        val measured = availableMah?.takeIf { pct >= 5f }?.let { it / (pct / 100f) }?.takeIf { it in VALID_MAH }
        val design = designMah
        val user = _userCapacity.value
        val (capacity, source) = when {
            user > 0 -> user.toFloat() to "Set by you"
            measured != null -> measured to "Reported by Android (charge counter)"
            design != null -> design to "Android design capacity"
            else -> DEFAULT_MAH to "Default (not reported by this phone)"
        }
        _capacity.value = CapacityInfo(design, availableMah, measured, capacity, source)
        return Sample(System.currentTimeMillis(), pct, ma * volts / 1000f, ma, temp, dir, capacity, volts)
    }

    private val _cards = MutableStateFlow(CardId.parse(prefs.getString("cards", null)).take(MAX_CARDS))
    val cards: StateFlow<List<CardId>> = _cards

    private fun saveCards(v: List<CardId>) {
        _cards.value = v
        prefs.edit().putString("cards", v.joinToString(",") { it.key }).apply()
    }

    fun setCardShown(id: CardId, shown: Boolean) {
        if (shown && id !in _cards.value && _cards.value.size >= MAX_CARDS) return
        saveCards(if (shown) (_cards.value - id) + id else _cards.value - id)
    }

    fun moveCard(from: Int, to: Int) {
        val l = _cards.value.toMutableList()
        if (from !in l.indices || to !in l.indices || from == to) return
        l.add(to, l.removeAt(from))
        saveCards(l)
    }

    fun setTheme(v: String) {
        _theme.value = v
        prefs.edit().putString("theme", v).apply()
    }

    fun setTarget(v: Int) {
        _target.value = v
        prefs.edit().putInt("target", v).apply()
    }

    fun setRecording(v: Boolean) {
        _recording.value = v
        prefs.edit().putBoolean("record", v).apply()
    }

    fun clearHistory() {
        _samples.value = _samples.value.takeLast(1)
        viewModelScope.launch(Dispatchers.IO) { file.delete() }
    }

    companion object {
        const val DEFAULT_UPDATE_MS = 500
        const val MAX_CARDS = 8
        const val MAX_SAMPLES = 20_000
        const val DEFAULT_MAH = 4000f
        val VALID_MAH = 1500f..25000f
    }
}
