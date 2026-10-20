package com.chargeview.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

internal val LocalDark = compositionLocalOf { true }

private val GreenDark = Color(0xFF87EDAA)
private val GreenLight = Color(0xFF1B8A4B)
private val AmberDark = Color(0xFFFFC875)
private val AmberLight = Color(0xFFB36A00)

@Composable
fun chargeGreen() = if (LocalDark.current) GreenDark else GreenLight

@Composable
fun dischargeAmber() = if (LocalDark.current) AmberDark else AmberLight

@Composable
internal fun dirColor(dir: Int) = if (dir < 0) dischargeAmber() else chargeGreen()

@Composable
fun ChargeViewTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = GreenDark,
            background = Color(0xFF0E1210),
            surface = Color(0xFF0E1210),
            surfaceVariant = Color(0xFF121715),
            onBackground = Color(0xFFF1F5F2),
            onSurface = Color(0xFFF1F5F2),
            onSurfaceVariant = Color(0xFF9CA9A1),
            outlineVariant = Color(0xFF26312B),
            secondaryContainer = Color(0xFF245637),
            onSecondaryContainer = Color(0xFFC4F3D2),
        )
    } else {
        lightColorScheme(
            primary = GreenLight,
            background = Color(0xFFF5F8F6),
            surface = Color(0xFFF5F8F6),
            surfaceVariant = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFF55635B),
            outlineVariant = Color(0xFFC9D6CE),
            secondaryContainer = Color(0xFFCDEBD8),
            onSecondaryContainer = Color(0xFF0E4A28),
        )
    }
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun ChargeViewApp(vm: BatteryViewModel) {
    val samples by vm.samples.collectAsState()
    val target by vm.target.collectAsState()
    val theme by vm.theme.collectAsState()
    val recording by vm.recording.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showCards by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = chargeGreen(),
                    selectedTextColor = chargeGreen(),
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text("Home") }, colors = colors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.List, null) }, label = { Text("History") }, colors = colors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Info, null) }, label = { Text("Diagnostics") }, colors = colors)
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") }, colors = colors)
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TopBar()
            when (tab) {
                0 -> HomeScreen(vm, samples, target, recording)
                1 -> HistoryScreen(samples)
                2 -> DiagnosticsScreen(vm, samples, target, recording)
                else -> if (showCards) CardsSettingsScreen(vm, samples) { showCards = false }
                else if (showHelp) HelpScreen { showHelp = false }
                else SettingsScreen(vm, theme, target, recording, { showCards = true }, { showHelp = true })
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF1B3A27)),
            contentAlignment = Alignment.Center,
        ) { Text("ϟ", color = GreenDark, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text("ChargeView", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    brush: Brush? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val fill = if (brush != null) Modifier.background(brush, shape)
    else Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape)
    Card(
        modifier.then(fill).border(BorderStroke(1.dp, borderColor), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) { content() }
}

@Composable
internal fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun Hero(samples: List<Sample>, last: Sample?, target: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val eta = glidedEta(samples, target)
    val dir = last?.dir ?: 0
    val color = dirColor(dir)
    val dark = LocalDark.current
    val brush = Brush.linearGradient(
        if (dark) listOf(Color(0xFF1A2B21), Color(0xFF111815))
        else listOf(Color(0xFFE3F4EA), Color(0xFFFFFFFF))
    )
    Panel(modifier.fillMaxWidth(), borderColor = color.copy(alpha = if (selected) 1f else 0.35f), brush = brush) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Battery charge", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold)) {
                                append(last?.level?.toInt()?.toString() ?: "—")
                            }
                            if (last != null) withStyle(SpanStyle(fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("%") }
                        }
                    )
                }
                Pill(when (dir) { 1 -> "Charging"; -1 -> "Discharging"; else -> "Not charging" }, color)
            }
            LinearProgress((last?.level ?: 0f) / 100f, color)
            Row(Modifier.fillMaxWidth()) {
                Text("Battery level", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    if (dir < 0) "Empty at 0%" else "Target $target%",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (eta.minutes != null && eta.minutes > 0f) {
                        Text("~${fmtDuration(eta.minutes)}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                        Text(
                            (if (dir > 0) "Until target" else "Until empty") + " · estimate",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(eta.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
                    }
                }
                val tag = when {
                    eta.minutes == null || eta.minutes <= 0f -> null
                    dir > 0 && (last?.level ?: 0f) >= 80f -> "Taper-aware"
                    else -> eta.confidence.ifBlank { null }
                }
                if (tag != null) Pill(tag, color)
            }
        }
    }
}

@Composable
internal fun LinearProgress(fraction: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(12.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
        )
    }
}

internal fun signed(v: Float, digits: Int = 1) =
    (if (v > 0f) "+" else if (v < 0f) "−" else "") + "%.${digits}f".format(abs(v))

@Composable
internal fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

@Composable
internal fun RecordingCard(samples: List<Sample>, recording: Boolean) {
    val mins = if (samples.size >= 2) (samples.last().t - samples.first().t) / 60_000f else 0f
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Session recording", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "${fmtDuration(mins)} · while app is open",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (recording) chargeGreen() else MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.width(6.dp))
            Text(if (recording) "Active" else "Off", color = if (recording) chargeGreen() else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = chargeGreen()
    Text(
        text,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, if (selected) c else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .background(if (selected) c.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (selected) c else MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
    )
}

@Composable
private fun HistoryScreen(samples: List<Sample>) {
    var range by rememberSaveable { mutableIntStateOf(1) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val ranges = listOf(60 to "1 h", 360 to "6 h", 1440 to "24 h", Int.MAX_VALUE to "All")
    val all = remember(samples) { sessions(samples) }
    val perPage = 3
    val pages = ((all.size + perPage - 1) / perPage).coerceAtLeast(1)
    val cur = page.coerceIn(0, pages - 1)

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
        val wide = maxWidth >= 600.dp
        val chart: @Composable (Modifier) -> Unit = { m ->
            Panel(m) {
                Column(Modifier.padding(14.dp)) {
                    Text("Battery level", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ranges.forEachIndexed { i, r -> RangeChip(r.second, range == i) { range = i } }
                    }
                    val last = samples.lastOrNull()?.t ?: 0L
                    val since = if (ranges[range].first == Int.MAX_VALUE) 0L else last - ranges[range].first * 60_000L
                    val pts = samples.filter { it.t >= since }.map { Pt(it.t, it.level, it.dir) }
                    LineChart(pts, "%", zeroLine = false, fixedRange = 0f..100f, modifier = Modifier.weight(1f).fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(chargeGreen(), "Charging")
                        LegendDot(dischargeAmber(), "Discharging")
                    }
                }
            }
        }
        val list: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recorded by ChargeView", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                all.drop(cur * perPage).take(perPage).forEach { SessionRow(it) }
                if (all.isEmpty()) {
                    Text(
                        "No sessions yet. ChargeView records while it is running; Android does not expose earlier battery history to apps.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ page = (cur - 1).coerceAtLeast(0) }, enabled = cur > 0) { Text("Newer") }
                    Spacer(Modifier.weight(1f))
                    Text("${cur + 1} / $pages", fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton({ page = cur + 1 }, enabled = cur < pages - 1) { Text("Older") }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chart(Modifier.weight(1f))
                list(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chart(Modifier.weight(1f))
                list(Modifier.height(280.dp))
            }
        }
    }
}

@Composable
private fun SessionRow(s: Session) {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val mins = (s.end - s.start) / 60_000f
    val color = dirColor(s.dir)
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.dir > 0) "+" else "−", color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (s.dir > 0) "Charging" else "Discharging"} ${s.startLevel.toInt()}% → ${s.endLevel.toInt()}%",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${fmt.format(Date(s.start))} · ${fmtDuration(mins)} · peak ${"%.1f".format(s.peakPowerW)} W",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(vm: BatteryViewModel, samples: List<Sample>, target: Int, recording: Boolean) {
    val l = samples.lastOrNull()
    val cap by vm.capacity.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Raw readings", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DiagRow("Battery level", l?.let { "%.0f %%".format(it.level) })
                DiagRow("Battery current", l?.let { "${signed(it.currentMa, 0)} mA" })
                DiagRow("Battery power", l?.let { "${signed(it.powerW)} W" })
                DiagRow("Capacity used for estimates", "%.0f mAh".format(cap.effectiveMah))
                DiagRow("Capacity source", cap.source)
                DiagRow("Design capacity (Android)", cap.designMah?.let { "%.0f mAh".format(it) })
                DiagRow("Available charge (Android)", cap.availableMah?.let { "%.0f mAh".format(it) })
                DiagRow("Full capacity (measured)", cap.measuredMah?.let { "%.0f mAh".format(it) })
                DiagRow("Time estimate range", Estimator.eta(samples, target).let { e -> if (e.low != null && e.high != null) "${fmtDuration(e.low)}–${fmtDuration(e.high)}" else null })
                DiagRow("Temperature", l?.let { "%.1f °C".format(it.tempC) })
                DiagRow("Readings in memory", "${samples.size}")
                DiagRow("Recording", if (recording) "On" else "Off")
            }
        }
        Text(
            "Power is battery-side (voltage × current), not charger or USB input. Capacity comes from Android when available, otherwise your setting or 4000 mAh.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SystemLoadPanel()
        TopAppsPanel(samples)
    }
}

@Composable
private fun SystemLoadPanel() {
    val ctx = LocalContext.current
    var load by remember { mutableStateOf<SystemLoad?>(null) }
    var own by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        var lastCpu = android.os.Process.getElapsedCpuTime()
        var lastReal = android.os.SystemClock.elapsedRealtime()
        while (true) {
            load = AppUsage.systemLoad(ctx)
            val cpu = android.os.Process.getElapsedCpuTime()
            val real = android.os.SystemClock.elapsedRealtime()
            if (real > lastReal) own = (cpu - lastCpu).toDouble() / (real - lastReal) * 100
            lastCpu = cpu; lastReal = real
            delay(2000)
        }
    }
    Text("System load (estimate)", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagRow("CPU clock vs maximum", load?.cpuFreqPct?.let { "%.0f %%".format(it) })
            DiagRow("Thermal state", load?.thermalStatus)
            DiagRow("Thermal headroom", load?.thermalHeadroom?.let { "%.0f %%".format(it * 100) })
            DiagRow("ChargeView CPU", own?.let { "%.1f %%".format(it) })
        }
    }
    Text(
        "Android does not give apps the true total CPU %. CPU clock shows how fast the cores are running, and thermal headroom is how close the phone is to throttling (100% = throttling). Both are only where the phone allows them.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TopAppsPanel(samples: List<Sample>) {
    val ctx = LocalContext.current
    var hours by rememberSaveable { mutableIntStateOf(1) }
    var access by remember { mutableStateOf(AppUsage.hasAccess(ctx)) }
    var apps by remember { mutableStateOf<List<AppUse>>(emptyList()) }
    LaunchedEffect(hours) {
        while (true) {
            access = AppUsage.hasAccess(ctx)
            apps = if (access) AppUsage.top(ctx, hours * 3_600_000L) else emptyList()
            delay(5000)
        }
    }
    val since = System.currentTimeMillis() - hours * 3_600_000L
    val win = samples.filter { it.t >= since }
    val drained = win.zipWithNext().sumOf { (a, b) -> (a.level - b.level).coerceAtLeast(0f).toDouble() }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Top apps by screen time", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { hours = if (hours == 1) 24 else 1 }) { Text(if (hours == 1) "Last 1h" else "Last 24h") }
    }
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!access) {
                Text("Allow usage access to see which apps you use most.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { AppUsage.openAccessSettings(ctx) }) { Text("Grant usage access") }
            } else if (apps.isEmpty()) {
                Text("No app usage recorded in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                apps.forEach { a ->
                    val share = "%.0f".format(a.share * 100)
                    val est = if (drained >= 1) " · ~%.0f %% battery".format(a.share * drained) else ""
                    DiagRow(a.label, "${fmtDuration(a.foregroundMs / 60000f)} · $share %$est")
                }
            }
        }
    }
    Text(
        "Estimate only: Android hides real per-app battery use from apps. Share is of listed apps' screen time; battery is that share of the drop ChargeView recorded in the period.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

private val HELP: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "Getting started" to listOf(
        "What it does" to "ChargeView watches your battery while it is on screen and shows what is happening: how fast it is charging or draining, how long that will take, and how the numbers have moved over the last hour.",
        "Who it is for" to "It is built for slow charging. When the battery nears full the charge rate drops, and ChargeView accounts for that taper instead of assuming the current rate continues.",
        "Living with it" to "Open it while charging or when you want to check drain. It reads the battery every few seconds and, if recording is on, saves the readings on your phone.",
    ),
    "Home screen" to listOf(
        "Top card" to "The first card is large and holds more detail than the others. Hold it and drag to move it, like any other card.",
        "Small cards" to "Each small card shows a value, a note, and a sparkline of the last 10 minutes so you can see at a glance whether it is rising or falling.",
        "Graph card" to "Tap any card to plot that measurement in the graph at the bottom. Tap the same card again to clear it.",
        "Scrolling" to "Home scrolls. The large card stays pinned at the top while the other cards and the graph move underneath.",
        "Rearranging" to "Hold a card and drag it to a new position. The first position is always the large card.",
        "Changing cards" to "Settings then Home screen cards turns cards on and off and shows what each one will display. Up to 8 cards can be shown.",
    ),
    "Reading the numbers" to listOf(
        "Charge and drain" to "Above 80% the charge rate slows down as the battery fills. ChargeView applies that taper, so the time estimate reflects the slower finish rather than the fast start.",
        "Time to target" to "How long until the charge target you chose is reached. While discharging the same card shows the time until empty instead.",
        "Time to empty" to "While charging, the Time card can also show how long the battery would last at the current rate of use.",
        "Confidence" to "The battery only reports whole percent steps. Early on, ChargeView has too few steps to be sure, and says so. The estimate settles after a few minutes.",
        "Estimates" to "Time and rate figures are estimates from recent readings, not promises. Heavy use, a dimmed screen or a paused charge will change them.",
    ),
    "Cards" to listOf(
        "Battery charge" to "Level as a percentage, the charging state, and the time to your target.",
        "Battery power" to "Power in watts. Positive means charging, negative means discharging. Extras show the 10 minute average and 30 minute peak for charging and for discharging separately.",
        "Time" to "Time to the charge target, or to empty while discharging. The large card adds the time of day the target is reached and how long the current state has lasted.",
        "SoC change" to "How much the level has moved since the last plug or unplug event, growing minute by minute up to 10 minutes.",
        "Rate" to "Percent per hour, from 1 minute up to a 30 minute peak. Positive while charging.",
        "Next 1%" to "How long the next whole percent step should take, with the recent step times.",
        "Temperature" to "Battery temperature in Celsius, with the 30 minute low, average and high.",
        "Battery voltage" to "Battery terminal voltage, with the 30 minute low, average and high.",
        "mAh" to "The current in milliamps and the total capacity the estimates use. The extras show how much sits in the battery and the rate in percent per hour.",
        "Charge moved" to "Milliamp-hours moved in the last minute, 5 minutes, 10 minutes and this event, with the share of total capacity.",
        "Energy" to "Watt-hours moved in the last minute, 5 minutes, 10 minutes and this event, with the average power of the event.",
    ),
    "History and recording" to listOf(
        "Record history" to "Settings has a Record history switch. When it is on, readings are saved on this phone while the app is running. Nothing is sent anywhere.",
        "History tab" to "Recorded sessions, newest first, and a graph of the battery level. Tap a session to see its details.",
        "Clear history" to "Settings has Clear recorded history, which deletes everything recorded so far.",
        "Not recorded" to "Readings taken while the app is closed are not saved. Recording only runs while ChargeView is open.",
    ),
    "Graphs" to listOf(
        "Touch to inspect" to "Drag a finger along the graph to read the value at that point. Release to go back to the live view.",
        "Time axis" to "The chip on the graph switches between log time and linear time. Log time stretches the recent seconds and squeezes older readings so a full hour fits, and the vertical lines mark each interval.",
        "Charging and discharging" to "Charging is drawn as a dashed line, discharging as a solid one.",
        "Y axis" to "Every graph is labelled on the left, and each card's graph keeps its own scale.",
    ),
    "Settings" to listOf(
        "Theme" to "Light, Dark, or Follow system.",
        "Charge target" to "The level the time estimates aim for. Sliding it to 80% is a good way to slow battery ageing.",
        "Update interval" to "How often the battery is read, from 0.1s to 60s. The default is 5s, which is easy on the battery. For faster intervals than 1s the numbers glide between readings rather than jumping.",
        "Battery capacity" to "Used to turn current into percent, and percent into time. It is read from Android where possible, otherwise 4000 mAh is assumed. Adjust it with the -100 and +100 buttons to match your phone's real capacity.",
        "Diagnostics" to "Raw readings, plus a system load estimate and the apps you have used most. Android does not give apps real per-app battery statistics, so that list is an estimate from screen time and needs usage access.",
    ),
    "The phone" to listOf(
        "Folding" to "On a foldable, ChargeView adapts when the phone is opened or closed, without restarting and without losing readings.",
        "Trouble reading the battery" to "Some phones report a voltage that never changes, or no capacity at all. The app falls back to sane defaults and says which source it used in Diagnostics.",
        "Not sure where to start" to "Leave everything at its defaults for a day of normal use, then look at the graph to see when the battery drains fastest.",
    ),
)

@Composable
private fun HelpScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Row(
            Modifier.padding(top = 6.dp).clickable(onClick = back).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\u2039 ", color = chargeGreen(), fontSize = 20.sp)
            Text("How to use ChargeView", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        HELP.forEach { (section, rows) ->
            Text(
                section, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { (title, body) ->
                        Column {
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(body, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text(
            "ChargeView 0.11 \u00b7 Developer Yen@yenchoi.com",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value ?: "Not available", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun SettingsScreen(
    vm: BatteryViewModel,
    theme: String,
    target: Int,
    recording: Boolean,
    openCards: () -> Unit,
    openHelp: () -> Unit,
) {
    val cards by vm.cards.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        Panel(Modifier.fillMaxWidth().clickable(onClick = openCards)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Home screen cards", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${cards.size} of ${CardId.entries.size} shown · hold and drag on Home to reorder",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Panel(Modifier.fillMaxWidth().clickable(onClick = openHelp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("How to use ChargeView", fontWeight = FontWeight.SemiBold)
                    Text(
                        "What each card shows and how to read the numbers",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("\u203a", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Theme", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark").forEach { (k, v) ->
                        RangeChip(v, theme == k) { vm.setTheme(k) }
                    }
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Charge target: $target%", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = target.toFloat(),
                    onValueChange = { vm.setTarget(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 9,
                )
                Text(
                    "Used for the time-to-target estimate while charging.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            val ms by vm.updateMs.collectAsState()
            fun label(v: Int) = if (v < 1000) "%.1fs".format(v / 1000f) else "${v / 1000}s"
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Update interval", fontWeight = FontWeight.SemiBold)
                listOf(listOf(100, 500, 1000, 2000), listOf(5000, 10000, 30000, 60000)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { v -> RangeChip(label(v), ms == v) { vm.setUpdateMs(v) } }
                    }
                }
                Text(
                    when {
                        ms < 1000 -> "Readings are taken every 1s; numbers glide between them every ${label(ms)}. Display runs 1s behind."
                        ms == 1000 -> "Readings every 1s. High battery use, and history grows faster."
                        ms == 2000 -> "Readings every 2s, numbers glide every 1s. Display runs 2s behind."
                        else -> "Readings every ${label(ms)}, numbers glide every 1s. Default is 5s."
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            val cap by vm.capacity.collectAsState()
            val user by vm.userCapacity.collectAsState()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Battery capacity", fontWeight = FontWeight.SemiBold)
                        Text(
                            "%.0f mAh · %s".format(cap.effectiveMah, cap.source),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton({ vm.setUserCapacity(((if (user > 0) user else cap.effectiveMah.toInt()) - 100).coerceAtLeast(1500)) }) { Text("−100") }
                    TextButton({ vm.setUserCapacity(((if (user > 0) user else cap.effectiveMah.toInt()) + 100).coerceAtMost(25000)) }) { Text("+100") }
                }
                if (user > 0) TextButton({ vm.setUserCapacity(0) }) { Text("Use automatic value") }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Record history", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Saves readings on this device while ChargeView is running.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(recording, { vm.setRecording(it) })
                }
                Button(
                    { vm.clearHistory() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Text("Clear recorded history") }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("About", fontWeight = FontWeight.SemiBold)
                DiagRow("App", "ChargeView")
                DiagRow("Developer", "Yen@yenchoi.com")
                DiagRow("Version", "0.11")
            }
        }
    }
}
