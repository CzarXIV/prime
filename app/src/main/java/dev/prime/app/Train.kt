package dev.prime.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Content guard training: every moment Prime catches is a rep you can win (walk away)
 * or a slip (continue). The level adapts: clean days earn less intervention, repeated
 * slips bring more support back.
 */

data class Level(val name: String, val desc: String)

val LEVELS = listOf(
    Level("COACH", "Full pause, every time, with a 60-second reset."),
    Level("NUDGE", "A small card. Quick, conscious choice."),
    Level("BANNER", "A light banner. You handle it."),
)

const val PROMOTE_DAYS = 7
private const val DAY_MS = 86_400_000L

data class CEvent(
    val t: Long,
    val won: Boolean,
    val pkg: String,
    val src: String,
    val mood: String = "",
    val place: String = "",
) {
    fun json(): JSONObject = JSONObject()
        .put("t", t).put("w", won).put("a", pkg).put("s", src).put("m", mood).put("p", place)

    val date: LocalDate get() = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
    val hour: Int get() = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).hour

    companion object {
        fun of(o: JSONObject) = CEvent(
            o.optLong("t"), o.optBoolean("w"), o.optString("a"), o.optString("s"),
            o.optString("m"), o.optString("p"),
        )
    }
}

val DEFAULT_WATCH_WORDS = listOf(
    // sites
    "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "spankbang", "brazzers",
    "onlyfans", "fansly", "chaturbate", "stripchat", "bongacams", "livejasmin", "eporner",
    "motherless", "rule34", "nhentai", "beeg", "tnaflix", "porntrex", "hqporner", "erome",
    // terms
    "porn", "porno", "pornstar", "nsfw", "xxx", "nudes", "nude pics", "naked pics", "hentai",
    "sex tape", "sex video", "sex videos", "leaked nudes", "onlyfans leak", "camgirl", "cam girl",
    "milf", "blowjob", "lewd", "r34", "nsfw content", "18+ content",
)

object Train {
    private val p get() = Store.prefs

    // ---- settings
    var guardOn: Boolean
        get() = p.getBoolean("cg_on", true)
        set(v) { p.edit().putBoolean("cg_on", v).apply(); Store.bump() }

    var imageOn: Boolean
        get() = p.getBoolean("cg_img", true)
        set(v) { p.edit().putBoolean("cg_img", v).apply(); Store.bump() }

    /** 0 = low, 1 = medium, 2 = high */
    var sensitivity: Int
        get() = p.getInt("cg_sens", 1)
        set(v) { p.edit().putInt("cg_sens", v.coerceIn(0, 2)).apply(); Store.bump() }

    val threshold: Float get() = floatArrayOf(0.85f, 0.75f, 0.65f)[sensitivity]

    var words: List<String>
        get() {
            val s = p.getString("cg_words", null) ?: return DEFAULT_WATCH_WORDS
            val a = JSONArray(s)
            return List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
        }
        set(v) {
            p.edit().putString("cg_words", JSONArray(v).toString()).apply()
            Store.bump()
        }

    /** Capabilities the running guard service actually has (written by the service). */
    var capText: Boolean
        get() = p.getBoolean("cap_text", false)
        set(v) { p.edit().putBoolean("cap_text", v).apply() }

    var capShot: Boolean
        get() = p.getBoolean("cap_shot", false)
        set(v) { p.edit().putBoolean("cap_shot", v).apply() }

    /** No new catches until this time. */
    var quietUntil: Long
        get() = p.getLong("cg_quiet", 0L)
        set(v) { p.edit().putLong("cg_quiet", v).apply() }

    // ---- level
    var level: Int
        get() = p.getInt("cg_level", 0).coerceIn(0, LEVELS.lastIndex)
        private set(v) {
            p.edit().putInt("cg_level", v.coerceIn(0, LEVELS.lastIndex))
                .putLong("cg_level_since", System.currentTimeMillis()).apply()
            Store.bump()
        }

    val levelSince: Long
        get() {
            val v = p.getLong("cg_level_since", 0L)
            if (v == 0L) {
                val now = System.currentTimeMillis()
                p.edit().putLong("cg_level_since", now).apply()
                return now
            }
            return v
        }

    fun chooseLevel(i: Int) {
        level = i
    }

    // ---- events
    fun events(): List<CEvent> {
        val s = p.getString("cg_events", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) { CEvent.of(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun saveEvents(list: List<CEvent>) {
        val a = JSONArray()
        list.takeLast(1000).forEach { a.put(it.json()) }
        p.edit().putString("cg_events", a.toString()).apply()
        Store.bump()
    }

    fun record(won: Boolean, pkg: String, src: String) {
        saveEvents(events() + CEvent(System.currentTimeMillis(), won, pkg, src))
        if (won) {
            p.edit().putInt("cg_total_reps", totalReps + 1).apply()
        }
        evaluate()
    }

    val totalReps: Int get() = p.getInt("cg_total_reps", 0)

    private fun lastSlipAt(): Long = events().lastOrNull { !it.won }?.t ?: 0L

    /** Move the level based on recent behaviour. Called after each decision and when the app opens. */
    fun evaluate() {
        val now = System.currentTimeMillis()
        val since = levelSince
        val recentSlips = events().count { !it.won && it.t >= since && it.t >= now - 3 * DAY_MS }
        if (recentSlips >= 2 && level > 0) {
            level -= 1
            return
        }
        val cleanFrom = maxOf(since, lastSlipAt())
        if (level < LEVELS.lastIndex && now - cleanFrom >= PROMOTE_DAYS * DAY_MS) {
            level += 1
        }
    }

    /** Days left before the next level is earned, or null at the top. */
    fun daysToNext(): Int? {
        if (level >= LEVELS.lastIndex) return null
        val cleanFrom = maxOf(levelSince, lastSlipAt())
        val elapsed = ((System.currentTimeMillis() - cleanFrom) / DAY_MS).toInt()
        return (PROMOTE_DAYS - elapsed).coerceAtLeast(1)
    }

    fun todaysSlips(): List<CEvent> {
        val today = LocalDate.now()
        return events().filter { !it.won && it.date == today }
    }

    fun reflectToday(mood: String, place: String) {
        val today = LocalDate.now()
        saveEvents(events().map {
            if (!it.won && it.date == today) it.copy(mood = mood, place = place) else it
        })
    }

    fun week(): Pair<Int, Int> {
        val from = System.currentTimeMillis() - 7 * DAY_MS
        val w = events().filter { it.t >= from }
        return w.count { it.won } to w.count { !it.won }
    }

    fun appLabel(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun patterns(ctx: Context): List<Pair<String, String>> {
        val slips = events().filter { !it.won }
        if (slips.size < 3) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        fun band(h: Int) = when (h) {
            in 23..23, in 0..1 -> "Late night (11pm–2am)"
            in 2..5 -> "Small hours (2am–6am)"
            in 6..11 -> "Morning"
            in 12..17 -> "Afternoon"
            else -> "Evening (6pm–11pm)"
        }
        fun <T> top(xs: List<T>): Pair<T, Int>? =
            xs.groupingBy { it }.eachCount().maxByOrNull { it.value }?.toPair()

        top(slips.map { band(it.hour) })?.let { (b, n) -> out += "When" to "$b · $n of ${slips.size}" }
        top(slips.map { it.pkg })?.let { (a, n) -> out += "Where on the phone" to "${appLabel(ctx, a)} · $n of ${slips.size}" }
        top(slips.mapNotNull { it.place.ifBlank { null } })?.let { (pl, n) -> out += "Where you were" to "$pl · $n" }
        top(slips.mapNotNull { it.mood.ifBlank { null } })?.let { (m, n) -> out += "How you felt" to "$m · $n" }
        return out
    }

    /** Per day for the last n days: 0 = nothing, 1 = only wins, 2 = had a slip. */
    fun dayMarks(n: Int): List<Int> {
        val ev = events()
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val de = ev.filter { it.date == d }
            when {
                de.isEmpty() -> 0
                de.any { !it.won } -> 2
                else -> 1
            }
        }
    }
}

val MOODS = listOf("Bored", "Tired", "Stressed", "Lonely", "Restless", "Fine")
val PLACES = listOf("In bed", "Bathroom", "At my desk", "Out", "Other")
