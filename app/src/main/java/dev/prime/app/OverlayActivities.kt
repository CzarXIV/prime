package dev.prime.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shown right after the phone is unlocked. */
class PrimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        primeEdgeToEdge()
        setContent {
            PrimeTheme {
                PrimeCard(
                    onGo = { finish() },
                    onPrime = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra("open", "morning")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
fun PrimeCard(onGo: () -> Unit, onPrime: () -> Unit) {
    val day = remember { Store.today() }
    val mode = remember { Store.mode }
    val streak = remember { Store.streak() }
    val line = remember {
        if (day.intention.isNotBlank()) day.intention else Store.principles.randomOrNull() ?: "Show up."
    }
    BackHandler { onGo() }
    Column(Modifier.fillMaxSize().background(P.Black).safeDrawingPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(P.Accent))
            HGap(10)
            Label("${mode.label} · day $streak", color = P.Text)
        }
        Spacer(Modifier.weight(1f))
        Label(if (day.morning) "Today's intention" else "Your code", color = P.Accent)
        Gap(12)
        Headline(line, 44.sp)
        if (day.morning) {
            Gap(24)
            day.tasks.forEachIndexed { i, t ->
                if (t.isNotBlank()) {
                    val done = day.done.getOrElse(i) { false }
                    Text(
                        "${i + 1}  $t",
                        color = if (done) P.Ghost else P.Muted,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (!day.morning) {
            BigButton("Prime my day", onClick = onPrime)
            Gap(10)
            BigButton("Later", filled = false, onClick = onGo)
        } else {
            BigButton("Go", onClick = onGo)
        }
    }
}

/** The pause before a distracting app opens. */
class FrictionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        primeEdgeToEdge()
        val pkg = intent.getStringExtra("pkg")
        if (pkg == null) {
            finish()
            return
        }
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("this app")
        val mode = Store.mode
        setContent {
            PrimeTheme {
                FrictionScreen(label, mode, onBack = { backToWork() }, onAllow = { allow(pkg, mode) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun backToWork() {
        Store.updateToday { it.copy(resisted = it.resisted + 1) }
        goHome(this)
        finish()
    }

    private fun allow(pkg: String, mode: Mode) {
        Store.grantPass(pkg, mode.passMin)
        Store.updateToday { it.copy(gaveIn = it.gaveIn + 1) }
        finish()
    }
}

private val REASONS = listOf("Reply to someone", "I need it for work", "Taking a real break", "Habit. Boredom.")

@Composable
fun FrictionScreen(appLabel: String, mode: Mode, onBack: () -> Unit, onAllow: () -> Unit) {
    var remaining by remember { mutableIntStateOf(mode.delaySec) }
    var reason by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().background(P.Black).safeDrawingPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(P.Accent))
            HGap(10)
            Label("${mode.label} mode", color = P.Accent)
        }
        Spacer(Modifier.weight(1f))
        Headline("Why are you opening $appLabel?", 40.sp)
        Gap(24)
        REASONS.forEach { r ->
            val selected = r == reason
            Box(
                Modifier.fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.5.dp, if (selected) P.Accent else P.Line)
                    .clickable { reason = r }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(r, color = if (selected) P.Accent else P.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (reason == REASONS.last()) {
            Gap(10)
            Text("Then you already know the answer.", color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.weight(1f))
        BigButton("Back to work", onClick = onBack)
        Gap(10)
        val ready = remaining == 0 && reason != null
        val label = when {
            remaining > 0 -> "Wait ${remaining}s"
            reason == null -> "Pick a reason"
            else -> "Open for ${mode.passMin} min"
        }
        BigButton(label, filled = false, enabled = ready, onClick = onAllow)
    }
}
