package dev.prime.app

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrainTab() {
    val ctx = LocalContext.current
    watchStore()
    LaunchedEffect(Unit) { Train.evaluate() }
    val reps = Train.totalReps
    val (wins, slips) = Train.week()
    val total = wins + slips
    val level = Train.level
    val toNext = Train.daysToNext()
    val patterns = Train.patterns(ctx)
    val marks = Train.dayMarks(14)
    val a11y = a11yEnabled(ctx)
    var showWords by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$reps",
                    color = P.Accent,
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 104.sp,
                    letterSpacing = (-4).sp,
                )
                HGap(14)
                Column(Modifier.padding(bottom = 18.dp)) {
                    Headline("REPS", 22.sp)
                    Gap(6)
                    Label("Times you walked away")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Win rate · 7d", if (total == 0) 0 else wins * 100 / total, P.Accent, Modifier.weight(1f), suffix = if (total == 0) "" else "%")
                Stat("Reps · 7d", wins, P.Text, Modifier.weight(1f))
                Stat("Slips · 7d", slips, P.Muted, Modifier.weight(1f))
            }
        }

        item {
            Column {
                Label("Last 14 days")
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    marks.forEach { m ->
                        val mod = when (m) {
                            1 -> Modifier.background(P.Accent)
                            2 -> Modifier.border(2.dp, P.Ghost)
                            else -> Modifier.background(P.Line)
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).then(mod))
                    }
                }
                Gap(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(P.Accent)); HGap(6)
                    Text("All walk-aways", color = P.Muted, fontSize = 12.sp); HGap(16)
                    Box(Modifier.size(10.dp).border(2.dp, P.Ghost)); HGap(6)
                    Text("Had a slip", color = P.Muted, fontSize = 12.sp)
                }
            }
        }

        item {
            Column {
                Label("Training level")
                Gap(4)
                Text(
                    "Clean days earn you less help. Two slips in three days bring more support back. Tap a level to set it yourself.",
                    color = P.Muted,
                    fontSize = 13.sp,
                )
                Gap(12)
                LEVELS.forEachIndexed { i, l ->
                    val on = i == level
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(if (on) Modifier.background(P.Accent) else Modifier.border(1.5.dp, P.Line))
                            .clickable { Train.chooseLevel(i) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}",
                            color = if (on) Color.Black else P.Accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.width(34.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(l.name, color = if (on) Color.Black else P.Text, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text(l.desc, color = if (on) Color.Black else P.Muted, fontSize = 13.sp)
                        }
                    }
                }
                Gap(10)
                Text(
                    if (toNext == null) "Top level. Hold it."
                    else "$toNext clean day${if (toNext == 1) "" else "s"} to earn ${LEVELS[level + 1].name}.",
                    color = P.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item {
            Column {
                Label("Patterns")
                Gap(8)
                if (patterns.isEmpty()) {
                    Text(
                        "Patterns show up after 3 slips. When you choose Continue, the evening review asks one question about it. That's how Prime learns your weak spots.",
                        color = P.Muted,
                        fontSize = 14.sp,
                    )
                } else {
                    patterns.forEach { (k, v) ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Label(k)
                            Gap(2)
                            Text(v, color = P.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Rule()
                    }
                    Gap(8)
                    Text("Change the environment around the weak spot, not just your willpower.", color = P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Column {
                Label("Content guard")
                Gap(4)
                ToggleRow("Guard on", "Catches the moment, hands you the choice", Train.guardOn) { Train.guardOn = it }
                if (Train.guardOn) {
                    if (!a11y) {
                        Text(
                            "The guard service is off. Turn it on in the GUARD tab.",
                            color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    } else if (!Train.capText) {
                        Text(
                            "Switch Prime off and on again in Accessibility settings so the guard can read on-screen text. TAP HERE →",
                            color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.padding(vertical = 6.dp),
                        )
                    }
                    val imgSupported = Build.VERSION.SDK_INT >= 30
                    ToggleRow(
                        "Image check",
                        if (imgSupported) "Checks the screen once a minute, on the phone. Nothing is saved."
                        else "Needs Android 11 or newer",
                        Train.imageOn && imgSupported,
                    ) { if (imgSupported) Train.imageOn = it }
                    if (Train.imageOn && imgSupported) {
                        StepperRow(
                            "Image sensitivity",
                            listOf("LOW", "MEDIUM", "HIGH")[Train.sensitivity],
                            onMinus = { Train.sensitivity = Train.sensitivity - 1 },
                            onPlus = { Train.sensitivity = Train.sensitivity + 1 },
                        )
                    }
                }
            }
        }

        item {
            Column {
                val words = Train.words
                Row(
                    Modifier.fillMaxWidth().clickable { showWords = !showWords }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Label("Watch words · ${words.size}")
                        Text("Words and sites the text check looks for", color = P.Muted, fontSize = 13.sp)
                    }
                    Text(if (showWords) "HIDE" else "SHOW", color = P.Accent, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                if (showWords) {
                    Gap(8)
                    WordChips(words)
                    Gap(12)
                    Field(draft, { draft = it }, "Add a word or site", size = 16.sp)
                    Gap(10)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("Add", Modifier.weight(1f), enabled = draft.isNotBlank()) {
                            val w = draft.trim().lowercase()
                            if (w !in words) Train.words = words + w
                            draft = ""
                        }
                        BigButton("Reset list", Modifier.weight(1f), filled = false) {
                            Train.words = DEFAULT_WATCH_WORDS
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordChips(words: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.forEach { w ->
            Row(
                Modifier.border(1.dp, P.Line).clickable { Train.words = words - w }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(w, color = P.Text, fontSize = 13.sp)
                HGap(6)
                Text("×", color = P.Muted, fontSize = 14.sp)
            }
        }
    }
}

/** Evening review block: one question about today's slips. */
@Composable
fun SlipReflection() {
    watchStore()
    val slips = Train.todaysSlips()
    if (slips.isEmpty()) return
    val first = slips.first()
    var mood by remember { mutableStateOf(first.mood) }
    var place by remember { mutableStateOf(first.place) }
    Column {
        Label("Content guard")
        Gap(6)
        Headline(
            "You chose Continue ${if (slips.size == 1) "once" else "${slips.size} times"} today. What was going on?",
            22.sp,
        )
        Gap(6)
        Text("No judgement. This is how you find the weak spot.", color = P.Muted, fontSize = 13.sp)
        Gap(14)
        Label("How you felt")
        Gap(8)
        Chips(MOODS, mood) { mood = it; Train.reflectToday(mood, place) }
        Gap(14)
        Label("Where you were")
        Gap(8)
        Chips(PLACES, place) { place = it; Train.reflectToday(mood, place) }
        Gap(28)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(options: List<String>, selected: String, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .then(if (on) Modifier.background(P.Accent) else Modifier.border(1.5.dp, P.Line))
                    .clickable { onPick(o) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(o, color = if (on) Color.Black else P.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
