package dev.prime.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun ComponentActivity.primeEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
}

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Reminders.schedule(this)
        primeEdgeToEdge()
        route.value = intent?.getStringExtra("open") ?: "home"
        setContent { PrimeTheme { App(route) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open")?.let { route.value = it }
    }

    override fun onResume() {
        super.onResume()
        Train.evaluate()
        Store.bump() // refresh permission status, counters from the guard
    }
}

@Composable
fun App(route: MutableState<String>) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Store.bump()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Store.askedNotif &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Store.askedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    BackHandler(enabled = route.value != "home") { route.value = "home" }
    when (route.value) {
        "morning" -> MorningScreen { route.value = "home" }
        "evening" -> EveningScreen { route.value = "home" }
        else -> HomeScaffold { route.value = it }
    }
}

// ---------------------------------------------------------------- Home

@Composable
fun HomeScaffold(onRitual: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(P.Black).safeDrawingPadding()) {
        Header()
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> TodayTab(onRitual)
                1 -> TrainTab()
                2 -> GuardTab()
                else -> CodeTab()
            }
        }
        TabBar(tab) { tab = it }
    }
}

@Composable
fun Header() {
    watchStore()
    val mode = Store.mode
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(P.Accent))
        HGap(10)
        Text("PRIME", color = P.Text, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Spacer(Modifier.weight(1f))
        Label(mode.label, color = if (mode.friction) P.Accent else P.Muted)
    }
}

@Composable
fun TabBar(tab: Int, onTab: (Int) -> Unit) {
    Column {
        Rule()
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            listOf("TODAY", "TRAIN", "GUARD", "CODE").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == tab) {
                        Box(
                            Modifier.align(Alignment.TopCenter).fillMaxWidth(0.5f).height(3.dp)
                                .background(P.Accent)
                        )
                    }
                    Text(
                        t,
                        color = if (i == tab) P.Text else P.Muted,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Today

@Composable
fun TodayTab(onRitual: (String) -> Unit) {
    val ctx = LocalContext.current
    watchStore()
    val day = Store.today()
    val streak = Store.streak()
    val best = Store.best()
    val mode = Store.mode
    val history = Store.history(21)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$streak",
                    color = P.Accent,
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 104.sp,
                    letterSpacing = (-4).sp,
                )
                HGap(14)
                Column(Modifier.padding(bottom = 18.dp)) {
                    Headline("DAY\nSTREAK", 22.sp)
                    Gap(6)
                    Label("Best $best")
                }
            }
        }

        item {
            Column {
                Label("Mode")
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mode.values().forEach { m ->
                        val active = m == mode
                        Box(
                            Modifier.weight(1f).height(88.dp)
                                .then(if (active) Modifier.background(P.Accent) else Modifier.border(1.5.dp, P.Line))
                                .clickable { setMode(ctx, m) }
                                .padding(12.dp)
                        ) {
                            Text(
                                m.label.replace(" ", "\n"),
                                color = if (active) Color.Black else P.Text,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                        }
                    }
                }
                Gap(10)
                val detail = if (mode.friction) "${mode.delaySec}s friction · ${mode.passMin} min pass" else "guard off"
                Text("${mode.tagline}  ·  $detail", color = P.Muted, fontSize = 13.sp)
            }
        }

        item {
            Column {
                if (day.morning) {
                    Label("Today's intention", color = P.Accent)
                    Gap(8)
                    Headline(day.intention, 30.sp)
                    Gap(14)
                    day.tasks.forEachIndexed { i, t ->
                        if (t.isNotBlank()) {
                            val done = day.done.getOrElse(i) { false }
                            Row(
                                Modifier.fillMaxWidth().clickable { Store.toggleTask(i) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CheckSquare(done)
                                HGap(14)
                                Text(
                                    t,
                                    color = if (done) P.Muted else P.Text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                )
                            }
                        }
                    }
                    Gap(18)
                    if (!day.evening) {
                        BigButton("Close the day", filled = false) { onRitual("evening") }
                    } else {
                        Label("Day closed · effort ${day.rating}/5", color = P.Accent)
                    }
                } else {
                    Headline("Your day isn't primed yet.", 30.sp)
                    Gap(16)
                    BigButton("Prime my day") { onRitual("morning") }
                }
            }
        }

        item {
            Column {
                Label("Today's scorecard")
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Resisted", day.resisted, P.Accent, Modifier.weight(1f))
                    Stat("Gave in", day.gaveIn, P.Text, Modifier.weight(1f))
                }
            }
        }

        item {
            Column {
                Label("Last 21 days")
                Gap(10)
                history.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { d ->
                            val c = when {
                                d.complete -> P.Accent
                                d.morning -> P.AccentDeep
                                else -> P.Line
                            }
                            Box(Modifier.weight(1f).aspectRatio(1f).background(c))
                        }
                    }
                    Gap(6)
                }
                Gap(6)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(P.Accent)); HGap(6)
                    Text("Primed + closed", color = P.Muted, fontSize = 12.sp); HGap(16)
                    Box(Modifier.size(10.dp).background(P.AccentDeep)); HGap(6)
                    Text("Primed only", color = P.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Stat(label: String, n: Int, color: Color, modifier: Modifier, suffix: String = "") {
    Column(modifier.border(1.5.dp, P.Line).padding(16.dp)) {
        Text("$n$suffix", color = color, fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 46.sp, maxLines = 1)
        Label(label)
    }
}

// ---------------------------------------------------------------- Guard

data class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap?)

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val list = pm.queryIntentActivities(intent, 0)
    return list
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { ri ->
            val icon = runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            AppEntry(ri.activityInfo.packageName, ri.loadLabel(pm).toString(), icon)
        }
        .sortedBy { it.label.lowercase() }
}

private val COOLDOWNS = listOf(0, 5, 15, 30, 60, 120)

@Composable
fun GuardTab() {
    val ctx = LocalContext.current
    watchStore()
    val a11y = a11yEnabled(ctx)
    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val blocked = Store.blocked
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadApps(ctx) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Column {
                Label("Setup")
                Gap(6)
                SetupRow(
                    "Guard service",
                    if (a11y) "Active. Watching for distractions." else "Off. Friction and the unlock screen won't run.",
                    a11y,
                ) {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                if (!a11y) {
                    Text(
                        "Find Prime under Accessibility → Downloaded apps and switch it on. If Android says it's a restricted setting, open App info, tap ⋮ (top right) → Allow restricted settings, then try again.",
                        color = P.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 26.dp, bottom = 6.dp),
                    )
                    Text(
                        "OPEN APP INFO →",
                        color = P.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier
                            .padding(start = 26.dp, bottom = 8.dp)
                            .clickable {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            .padding(vertical = 6.dp),
                    )
                }
                SetupRow(
                    "Notifications",
                    if (notif) "On. Ritual reminders will reach you." else "Off. You won't get ritual reminders.",
                    notif,
                ) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

        item {
            Column {
                Gap(28)
                Label("Behaviour")
                Gap(4)
                ToggleRow("Intention on unlock", "Your intention or code, every time you unlock", Store.unlockEnabled) {
                    Store.unlockEnabled = it
                }
                if (Store.unlockEnabled) {
                    val cd = Store.unlockCooldownMin
                    val idx = COOLDOWNS.indexOf(cd).coerceAtLeast(0)
                    StepperRow(
                        "Show at most every",
                        if (cd == 0) "ALWAYS" else "$cd MIN",
                        onMinus = { Store.unlockCooldownMin = COOLDOWNS[(idx - 1).coerceAtLeast(0)] },
                        onPlus = { Store.unlockCooldownMin = COOLDOWNS[(idx + 1).coerceAtMost(COOLDOWNS.lastIndex)] },
                    )
                }
                ToggleRow("Mode wallpaper", "Home and lock screen show your current mode", Store.wallpaperEnabled) {
                    Store.wallpaperEnabled = it
                    if (it) PrimeWallpaper.apply(ctx)
                }
                StepperRow(
                    "Morning reminder",
                    hourLabel(Store.morningHour),
                    onMinus = { Store.morningHour = (Store.morningHour + 23) % 24; Reminders.schedule(ctx) },
                    onPlus = { Store.morningHour = (Store.morningHour + 1) % 24; Reminders.schedule(ctx) },
                )
                StepperRow(
                    "Evening reminder",
                    hourLabel(Store.eveningHour),
                    onMinus = { Store.eveningHour = (Store.eveningHour + 23) % 24; Reminders.schedule(ctx) },
                    onPlus = { Store.eveningHour = (Store.eveningHour + 1) % 24; Reminders.schedule(ctx) },
                )
            }
        }

        item {
            Column {
                Gap(28)
                Label("Distraction list · ${blocked.size} apps")
                Gap(6)
                Text(
                    "These get a pause and a question before they open, in Deep Work and Learning.",
                    color = P.Muted,
                    fontSize = 13.sp,
                )
                Gap(8)
                Field(query, { query = it }, "Search apps", size = 16.sp)
                Gap(8)
            }
        }

        val list = apps
        if (list == null) {
            item { Text("Loading apps…", color = P.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp)) }
        } else {
            val filtered = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            items(filtered, key = { it.pkg }) { app ->
                AppRow(app, app.pkg in blocked) { Store.toggleBlocked(app.pkg) }
            }
        }
    }
}

fun hourLabel(h: Int): String = String.format("%02d:00", h)

@Composable
fun SetupRow(title: String, sub: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).then(if (ok) Modifier.background(P.Accent) else Modifier.border(2.dp, P.Ghost)))
        HGap(14)
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = P.Muted, fontSize = 13.sp)
        }
        HGap(10)
        Text(
            if (ok) "ON" else "FIX →",
            color = if (ok) P.Muted else P.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
fun AppRow(app: AppEntry, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(Modifier.size(36.dp).background(P.Line))
        }
        HGap(14)
        Text(
            app.label,
            color = if (on) P.Text else P.Muted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HGap(10)
        CheckSquare(on)
    }
}

// ---------------------------------------------------------------- Code

@Composable
fun CodeTab() {
    watchStore()
    val list = Store.principles
    var draft by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Column {
                Headline("YOUR CODE", 44.sp)
                Gap(8)
                Text(
                    "The lines you live by. One opens every morning ritual, and one shows on unlock when you haven't set an intention.",
                    color = P.Muted,
                    fontSize = 14.sp,
                )
                Gap(16)
                Rule()
            }
        }
        itemsIndexed(list) { i, p ->
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        String.format("%02d", i + 1),
                        color = P.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(36.dp).padding(top = 4.dp),
                    )
                    Text(p, color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        "×",
                        color = P.Muted,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clickable { Store.principles = list.filterIndexed { j, _ -> j != i } }
                            .padding(horizontal = 10.dp),
                    )
                }
                Rule()
            }
        }
        item {
            Column {
                Gap(20)
                Field(draft, { draft = it }, "Add a line you live by", size = 18.sp, singleLine = false)
                Gap(14)
                BigButton("Add", enabled = draft.isNotBlank()) {
                    Store.principles = list + draft.trim()
                    draft = ""
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Rituals

@Composable
fun RitualTop(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(P.Accent))
        HGap(10)
        Label(title, color = P.Accent)
        Spacer(Modifier.weight(1f))
        Text(
            "✕",
            color = P.Muted,
            fontSize = 22.sp,
            modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
        )
    }
}

@Composable
fun MorningScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val today = remember { Store.today() }
    val principle = remember { Store.principles.randomOrNull() ?: "Show up." }
    var intention by remember { mutableStateOf(today.intention) }
    val tasks = remember { mutableStateListOf("", "", "").also { l -> for (i in 0..2) l[i] = today.tasks.getOrElse(i) { "" } } }

    Column(
        Modifier.fillMaxSize().background(P.Black).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        RitualTop("Morning priming", onDone)
        Gap(28)
        Headline(principle, 34.sp)
        Gap(44)
        Label("One intention for today")
        Field(intention, { intention = it }, "What wins today?", size = 22.sp, singleLine = false)
        Gap(36)
        Label("Top 3 · non-negotiable")
        for (i in 0..2) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${i + 1}",
                    color = P.Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(30.dp).padding(bottom = 14.dp),
                )
                Field(tasks[i], { tasks[i] = it }, "Task", modifier = Modifier.weight(1f), size = 18.sp)
            }
        }
        Gap(44)
        BigButton("Lock it in", enabled = intention.isNotBlank()) {
            Store.saveMorning(intention.trim(), tasks.map { it.trim() })
            if (Store.wallpaperEnabled) PrimeWallpaper.apply(ctx)
            onDone()
        }
        Gap(20)
    }
}

@Composable
fun EveningScreen(onDone: () -> Unit) {
    watchStore()
    val day = Store.today()
    var wins by remember { mutableStateOf(day.wins) }
    var lesson by remember { mutableStateOf(day.lesson) }
    var rating by remember { mutableIntStateOf(day.rating) }

    Column(
        Modifier.fillMaxSize().background(P.Black).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        RitualTop("Evening review", onDone)
        Gap(28)
        Headline("How did you show up today?", 34.sp)
        if (day.intention.isNotBlank()) {
            Gap(10)
            Text("You set out to: ${day.intention}", color = P.Muted, fontSize = 15.sp)
        }
        Gap(36)
        if (day.tasks.any { it.isNotBlank() }) {
            Label("Top 3")
            day.tasks.forEachIndexed { i, t ->
                if (t.isNotBlank()) {
                    val done = day.done.getOrElse(i) { false }
                    Row(
                        Modifier.fillMaxWidth().clickable { Store.toggleTask(i) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckSquare(done)
                        HGap(14)
                        Text(t, color = if (done) P.Muted else P.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Gap(28)
        }
        SlipReflection()
        Label("Wins")
        Field(wins, { wins = it }, "What went right?", size = 18.sp, singleLine = false)
        Gap(28)
        Label("Do better tomorrow")
        Field(lesson, { lesson = it }, "One thing to sharpen", size = 18.sp, singleLine = false)
        Gap(28)
        Label("Rate your effort")
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                val on = n <= rating
                Box(
                    Modifier.weight(1f).height(56.dp)
                        .then(if (on) Modifier.background(P.Accent) else Modifier.border(1.5.dp, P.Line))
                        .clickable { rating = n },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", color = if (on) Color.Black else P.Text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Gap(44)
        BigButton("Close the day", enabled = rating > 0) {
            Store.saveEvening(wins.trim(), lesson.trim(), rating)
            onDone()
        }
        Gap(20)
    }
}
