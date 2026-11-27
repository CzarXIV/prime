package dev.prime.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shown when a locked Settings screen is closed. */
class StrictBlockActivity : ComponentActivity() {
    private val what = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        primeEdgeToEdge()
        what.value = intent.getStringExtra("what") ?: "This screen"
        setContent {
            PrimeTheme {
                BackHandler { leave() }
                StrictBlockScreen(what.value) { leave() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        what.value = intent.getStringExtra("what") ?: "This screen"
    }

    private fun leave() {
        goHome(this)
        finish()
    }
}

@Composable
fun StrictBlockScreen(what: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(P.Black).safeDrawingPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(P.Accent))
            HGap(10)
            Label("Strict mode · ${Strict.left()} left", color = P.Accent)
        }
        Spacer(Modifier.weight(1f))
        Headline("Locked.", 64.sp)
        Gap(16)
        Text(what, color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Gap(6)
        Text("stays closed until ${Strict.endLabel()}.", color = P.Muted, fontSize = 16.sp)
        Gap(28)
        Text("You decided this when you were thinking clearly. Trust that version of you.", color = P.Muted, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        BigButton("Back to it", onClick = onBack)
    }
}

/** Strict content stop: one way out. */
@Composable
fun StrictStopScreen(onLeave: () -> Unit) {
    val line = remember { Store.principles.randomOrNull() ?: "Show up." }
    Column(Modifier.fillMaxSize().background(P.Black).safeDrawingPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(P.Accent))
            HGap(10)
            Label("Strict mode · ${Strict.left()} left", color = P.Accent)
        }
        Spacer(Modifier.weight(1f))
        Headline("Not this.", 52.sp)
        Headline("Not now.", 52.sp, color = P.Accent)
        Gap(28)
        Text(line, color = P.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        BigButton("Leave", onClick = onLeave)
    }
}

/** The Strict mode block on the TRAIN tab. */
@Composable
fun StrictSection() {
    val ctx = LocalContext.current
    watchStore()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
            Store.bump()
        }
    }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Store.bump()
    }
    val active = Strict.active
    val a11y = a11yEnabled(ctx) && Train.capText
    val admin = Strict.adminActive(ctx)
    var pick by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var confirming by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth()
            .then(if (active) Modifier.border(2.dp, P.Accent) else Modifier.border(1.5.dp, P.Line))
            .padding(18.dp)
    ) {
        Label(if (active) "Strict mode · on" else "Strict mode", color = P.Accent)
        Gap(10)

        if (active) {
            Text(
                Strict.left(),
                color = P.Text,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 64.sp,
                letterSpacing = (-2).sp,
            )
            Label("Left · ends ${Strict.endLabel()}")
            Gap(6)
            Text(
                "Stopped ${Strict.sessionStops} time${if (Strict.sessionStops == 1) "" else "s"} this session.",
                color = P.Muted, fontSize = 13.sp,
            )
            if (!a11y || !admin) {
                Gap(12)
                Text(
                    if (!a11y) "The guard service is off, so Strict mode can't enforce anything. Switch it back on."
                    else "Uninstall protection is off.",
                    color = P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (!admin) adminLauncher.launch(Strict.adminIntent(ctx))
                        else ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.padding(vertical = 4.dp),
                )
            }
            Gap(20)
            Label("Locked until then")
            Gap(6)
            LockedRow("Content stops", "Leave is the only option")
            LockedRow("Content guard settings", "Can only get stricter")
            LockedRow("Uninstalling Prime", "Prime is a device admin")
            SettingsMap.RULES.forEach { LockedRow(it.name, it.why) }

            Gap(20)
            Label("Extend")
            Gap(8)
            val options = Strict.DURATIONS.filter { Strict.canExtendBy(it.second) }
            if (options.isEmpty()) {
                Text("Already at the 30-day limit.", color = P.Muted, fontSize = 13.sp)
            } else {
                Chips(options.map { "+" + it.first }, pick?.let { "+" + it.first } ?: "") { l ->
                    pick = options.first { "+" + it.first == l }; confirming = true
                }
            }
        } else {
            Headline("Lock it in.", 32.sp)
            Gap(8)
            Text(
                "Pick a time. Until it runs out, Leave is the only option, the content guard can only get stricter, Prime can't be uninstalled, and the Settings screens that could switch Prime off are closed on sight. There's no early exit.",
                color = P.Muted, fontSize = 14.sp,
            )
            Gap(12)
            SetupRow(
                "Guard service",
                if (a11y) "Ready" else "Needs to be on, with screen reading allowed",
                a11y,
            ) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SetupRow(
                "Uninstall protection",
                if (admin) "Ready" else "Lets Prime block uninstalling",
                admin,
            ) { adminLauncher.launch(Strict.adminIntent(ctx)) }
            Gap(14)
            Label("How long")
            Gap(8)
            Chips(Strict.DURATIONS.map { it.first }, pick?.first ?: "") { l ->
                pick = Strict.DURATIONS.first { it.first == l }; confirming = false
            }
            Gap(16)
            if (!confirming) {
                BigButton("Start Strict mode", enabled = a11y && admin && pick != null) { confirming = true }
            }
            if (admin) {
                Gap(12)
                Text(
                    "REMOVE UNINSTALL PROTECTION",
                    color = P.Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    modifier = Modifier.clickable { Strict.removeAdmin(ctx) }.padding(vertical = 6.dp),
                )
            }
        }

        val chosen = pick
        if (confirming && chosen != null) {
            val end = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").format(
                java.time.Instant.ofEpochMilli(
                    minOf((if (active) Strict.until else System.currentTimeMillis()) + chosen.second,
                        System.currentTimeMillis() + Strict.MAX)
                ).atZone(java.time.ZoneId.systemDefault())
            )
            Gap(16)
            Column(Modifier.fillMaxWidth().background(P.AccentDeep).padding(16.dp)) {
                Headline(if (active) "Add ${chosen.first}?" else "Lock for ${chosen.first}?", 24.sp)
                Gap(6)
                Text(
                    "Strict mode will run until $end. You won't be able to end it early.",
                    color = P.Text, fontSize = 14.sp,
                )
                Gap(14)
                BigButton("Lock it") {
                    Strict.lockFor(chosen.second)
                    confirming = false
                    pick = null
                }
                Gap(8)
                BigButton("Cancel", filled = false) { confirming = false; pick = null }
            }
        }
    }
}

@Composable
private fun LockedRow(title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).background(P.Accent))
        Box(Modifier.width(12.dp))
        Column {
            Text(title, color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = P.Muted, fontSize = 12.sp)
        }
    }
}
