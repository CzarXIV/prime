package dev.prime.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shared decision handling for both pause styles. */
abstract class ContentMomentActivity : ComponentActivity() {
    protected var pkg = ""
    protected var src = ""
    private var decided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        primeEdgeToEdge()
        readIntent(intent)
        setContent { PrimeTheme { Screen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        decided = false
    }

    private fun readIntent(i: Intent) {
        pkg = i.getStringExtra("pkg") ?: ""
        src = i.getStringExtra("src") ?: ""
    }

    @Composable
    abstract fun Screen()

    fun walkAway() {
        if (!decided) {
            decided = true
            Train.record(true, pkg, src)
        }
        goHome(this)
        finish()
    }

    fun carryOn() {
        if (!decided) {
            decided = true
            Train.record(false, pkg, src)
            Train.quietUntil = System.currentTimeMillis() + 10 * 60_000L
        }
        finish()
    }
}

/** Level 1: full pause. */
class ContentPauseActivity : ContentMomentActivity() {
    @Composable
    override fun Screen() {
        var resetting by remember { mutableStateOf(false) }
        BackHandler { walkAway() }
        if (resetting) ResetScreen(onDone = { walkAway() })
        else PauseScreen(onWalk = { walkAway() }, onReset = { resetting = true }, onContinue = { carryOn() })
    }
}

/** Level 2: compact card over the app. */
class ContentNudgeActivity : ContentMomentActivity() {
    @Composable
    override fun Screen() {
        BackHandler { walkAway() }
        Box(Modifier.fillMaxSize().background(Color(0xB3000000))) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(P.Black)
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).background(P.Accent))
                Gap(16)
                Headline("Heads up. Your call.", 28.sp)
                Gap(6)
                Text("Rep #${Train.totalReps + 1} is right here.", color = P.Muted, fontSize = 14.sp)
                Gap(20)
                BigButton("Walk away") { walkAway() }
                Gap(10)
                BigButton("Continue", filled = false) { carryOn() }
            }
        }
    }
}

@Composable
fun PauseScreen(onWalk: () -> Unit, onReset: () -> Unit, onContinue: () -> Unit) {
    val line = remember { Store.principles.randomOrNull() ?: "Show up." }
    val reps = remember { Train.totalReps }
    Column(Modifier.fillMaxSize().background(P.Black).safeDrawingPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(P.Accent))
            HGap(10)
            Label("Content guard · ${reps} reps so far", color = P.Accent)
        }
        Spacer(Modifier.weight(1f))
        Headline("You're about to go there.", 44.sp)
        Gap(8)
        Headline("Your call.", 44.sp, color = P.Accent)
        Gap(28)
        Text(line, color = P.Muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        BigButton("Walk away", onClick = onWalk)
        Gap(10)
        BigButton("Give me 60 seconds", filled = false, onClick = onReset)
        Gap(18)
        Text(
            "Continue",
            color = P.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onContinue).padding(10.dp),
        )
    }
}

private val RESET_PROMPTS = listOf(
    "Drop and do 10 push-ups.",
    "Stand up. Walk to another room.",
    "Splash cold water on your face.",
    "10 slow breaths. Count them.",
    "Do 20 squats.",
)

@Composable
fun ResetScreen(onDone: () -> Unit) {
    var left by remember { mutableIntStateOf(60) }
    val prompt = remember { RESET_PROMPTS.random() }
    val line = remember { Store.principles.randomOrNull() ?: "Show up." }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    val t = rememberInfiniteTransition(label = "breath")
    val scale by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    // Square grows for 4s (breathe in), shrinks for 4s (breathe out).
    val inhale = ((60 - left) / 4) % 2 == 0

    Column(Modifier.fillMaxSize().background(P.Black).safeDrawingPadding().padding(24.dp)) {
        Label("Reset · ${left}s", color = P.Accent)
        Gap(16)
        Headline(prompt, 34.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size((200 * scale).dp).background(P.Accent))
            }
        }
        Gap(14)
        Text(
            if (inhale) "BREATHE IN" else "BREATHE OUT",
            color = P.Text,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        Text(line, color = P.Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Gap(20)
        if (left == 0) {
            BigButton("Done. That's a rep.", onClick = onDone)
        } else {
            BigButton("Walk away now", filled = false, onClick = onDone)
        }
    }
}
