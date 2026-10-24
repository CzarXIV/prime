package dev.prime.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class Mode(
    val label: String,
    val tagline: String,
    val delaySec: Int,
    val passMin: Int,
    val friction: Boolean,
) {
    DEEP("DEEP WORK", "No negotiation. Build.", 30, 5, true),
    LEARN("LEARNING", "Sharpen the blade.", 15, 10, true),
    REST("REST", "Recover on purpose.", 0, 0, false),
}

data class Day(
    val date: String,
    val intention: String = "",
    val tasks: List<String> = emptyList(),
    val done: List<Boolean> = emptyList(),
    val morning: Boolean = false,
    val evening: Boolean = false,
    val wins: String = "",
    val lesson: String = "",
    val rating: Int = 0,
    val resisted: Int = 0,
    val gaveIn: Int = 0,
) {
    val complete: Boolean get() = morning && evening

    fun toJson(): String = JSONObject()
        .put("date", date)
        .put("intention", intention)
        .put("tasks", JSONArray(tasks))
        .put("done", JSONArray(done))
        .put("morning", morning)
        .put("evening", evening)
        .put("wins", wins)
        .put("lesson", lesson)
        .put("rating", rating)
        .put("resisted", resisted)
        .put("gaveIn", gaveIn)
        .toString()

    companion object {
        fun fromJson(s: String): Day {
            val o = JSONObject(s)
            val t = o.optJSONArray("tasks") ?: JSONArray()
            val d = o.optJSONArray("done") ?: JSONArray()
            return Day(
                date = o.optString("date"),
                intention = o.optString("intention"),
                tasks = List(t.length()) { t.optString(it) },
                done = List(d.length()) { d.optBoolean(it) },
                morning = o.optBoolean("morning"),
                evening = o.optBoolean("evening"),
                wins = o.optString("wins"),
                lesson = o.optString("lesson"),
                rating = o.optInt("rating"),
                resisted = o.optInt("resisted"),
                gaveIn = o.optInt("gaveIn"),
            )
        }
    }
}

val DEFAULT_PRINCIPLES = listOf(
    "The work doesn't care how you feel. Show up.",
    "Obsess over the process. The results follow.",
    "Every miss is data. Adjust and go again.",
    "Do the reps nobody sees.",
    "Be uncomfortable on purpose.",
    "Finish what you start today.",
    "Compete with who you were yesterday.",
)

object Store {
    private lateinit var prefs: SharedPreferences
    val version = mutableIntStateOf(0)

    fun init(ctx: Context) {
        if (!::prefs.isInitialized) {
            prefs = ctx.applicationContext.getSharedPreferences("prime", Context.MODE_PRIVATE)
        }
    }

    fun bump() {
        version.intValue = version.intValue + 1
    }

    // ---- Mode ----
    var mode: Mode
        get() = runCatching { Mode.valueOf(prefs.getString("mode", "DEEP") ?: "DEEP") }.getOrDefault(Mode.DEEP)
        set(v) {
            prefs.edit().putString("mode", v.name).apply(); bump()
        }

    // ---- Distraction list ----
    var blocked: Set<String>
        get() = prefs.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()
        set(v) {
            prefs.edit().putStringSet("blocked", HashSet(v)).apply(); bump()
        }

    fun toggleBlocked(pkg: String) {
        val b = blocked
        blocked = if (pkg in b) b - pkg else b + pkg
    }

    fun passUntil(pkg: String): Long = prefs.getLong("pass_$pkg", 0L)

    fun grantPass(pkg: String, minutes: Int) {
        prefs.edit().putLong("pass_$pkg", System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    // ---- Principles ----
    var principles: List<String>
        get() {
            val s = prefs.getString("principles", null) ?: return DEFAULT_PRINCIPLES
            val a = JSONArray(s)
            return List(a.length()) { a.optString(it) }
        }
        set(v) {
            prefs.edit().putString("principles", JSONArray(v).toString()).apply(); bump()
        }

    // ---- Settings ----
    private fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply(); bump()
    }
    private fun int(key: String, def: Int) = prefs.getInt(key, def)
    private fun setInt(key: String, v: Int) {
        prefs.edit().putInt(key, v).apply(); bump()
    }

    var unlockEnabled: Boolean
        get() = bool("unlock", true)
        set(v) = setBool("unlock", v)

    var unlockCooldownMin: Int
        get() = int("cooldown", 15)
        set(v) = setInt("cooldown", v)

    var lastUnlockShown: Long
        get() = prefs.getLong("lastUnlock", 0L)
        set(v) {
            prefs.edit().putLong("lastUnlock", v).apply()
        }

    var wallpaperEnabled: Boolean
        get() = bool("wallpaper", false)
        set(v) = setBool("wallpaper", v)

    var morningHour: Int
        get() = int("morningHour", 6)
        set(v) = setInt("morningHour", v)

    var eveningHour: Int
        get() = int("eveningHour", 21)
        set(v) = setInt("eveningHour", v)

    var askedNotif: Boolean
        get() = bool("askedNotif", false)
        set(v) {
            prefs.edit().putBoolean("askedNotif", v).apply()
        }

    // ---- Days ----
    fun day(d: LocalDate): Day {
        val s = prefs.getString("day_$d", null) ?: return Day(d.toString())
        return runCatching { Day.fromJson(s) }.getOrDefault(Day(d.toString()))
    }

    fun today(): Day = day(LocalDate.now())

    fun save(day: Day) {
        prefs.edit().putString("day_${day.date}", day.toJson()).apply()
        bump()
    }

    fun updateToday(f: (Day) -> Day) = save(f(today()))

    fun saveMorning(intention: String, tasks: List<String>) = updateToday { d ->
        d.copy(
            intention = intention,
            tasks = tasks,
            done = tasks.indices.map { d.done.getOrElse(it) { false } },
            morning = true,
        )
    }

    fun saveEvening(wins: String, lesson: String, rating: Int) {
        updateToday { it.copy(wins = wins, lesson = lesson, rating = rating, evening = true) }
        val s = streak()
        if (s > prefs.getInt("best", 0)) prefs.edit().putInt("best", s).apply()
        bump()
    }

    fun toggleTask(i: Int) = updateToday { d ->
        val done = d.tasks.indices.map { d.done.getOrElse(it) { false } }.toMutableList()
        if (i in done.indices) done[i] = !done[i]
        d.copy(done = done)
    }

    fun streak(): Int {
        var d = LocalDate.now()
        if (!day(d).complete) d = d.minusDays(1)
        var n = 0
        while (n < 3650 && day(d).complete) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun best(): Int = maxOf(prefs.getInt("best", 0), streak())

    fun history(n: Int): List<Day> {
        val now = LocalDate.now()
        return (n - 1 downTo 0).map { day(now.minusDays(it.toLong())) }
    }
}

/** Read inside a composable to recompose whenever the store changes. */
@Composable
fun watchStore(): Int = Store.version.intValue
