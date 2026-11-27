package dev.prime.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Strict mode: a timed lock you can't undo.
 *  - content guard: Leave is the only option
 *  - guard settings can only get stricter
 *  - Prime is a device admin, so it can't be uninstalled
 *  - Settings screens that could switch Prime off are closed on sight (see [SettingsMap])
 *  - moving the phone clock does not shorten the lock
 */
object Strict {
    private val p get() = Store.prefs

    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
    const val MAX = 30 * DAY

    val DURATIONS: List<Pair<String, Long>> = listOf(
        "1 hour" to HOUR,
        "3 hours" to 3 * HOUR,
        "6 hours" to 6 * HOUR,
        "12 hours" to 12 * HOUR,
        "1 day" to DAY,
        "2 days" to 2 * DAY,
        "3 days" to 3 * DAY,
        "7 days" to 7 * DAY,
        "14 days" to 14 * DAY,
        "30 days" to 30 * DAY,
    )

    var until: Long
        get() = p.getLong("strict_until", 0L)
        private set(v) {
            p.edit().putLong("strict_until", v).apply(); Store.bump()
        }

    val active: Boolean get() = System.currentTimeMillis() < until
    val remaining: Long get() = (until - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Start, or extend while active. Never shortens. Capped at 30 days from now. */
    fun lockFor(ms: Long) {
        val now = System.currentTimeMillis()
        val base = if (active) until else now
        val target = minOf(base + ms, now + MAX)
        if (target <= until) return
        if (!active) p.edit().putInt("strict_stops_session", 0).apply()
        until = target
        Train.guardOn = true
        resetAnchor()
    }

    fun canExtendBy(ms: Long): Boolean = remaining + ms <= MAX + 60_000L

    var stops: Int
        get() = p.getInt("strict_stops", 0)
        set(v) { p.edit().putInt("strict_stops", v).apply(); Store.bump() }

    fun recordStop() {
        stops = stops + 1
        p.edit().putInt("strict_stops_session", p.getInt("strict_stops_session", 0) + 1).apply()
    }

    val sessionStops: Int get() = p.getInt("strict_stops_session", 0)

    // ---- clock protection: wall clock minus uptime only jumps when someone sets the time.
    private var bootCount = -1

    fun resetAnchor() {
        p.edit()
            .putLong("strict_anchor", System.currentTimeMillis() - SystemClock.elapsedRealtime())
            .putInt("strict_boot", bootCount)
            .apply()
    }

    /** Call often from the guard. If the clock was moved, move the end time by the same amount. */
    fun checkClock(ctx: Context) {
        bootCount = runCatching {
            android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
        }.getOrDefault(-1)
        val now = System.currentTimeMillis()
        val anchorNow = now - SystemClock.elapsedRealtime()
        val stored = p.getLong("strict_anchor", Long.MIN_VALUE)
        // After a reboot uptime restarts, so the old anchor means nothing: start again.
        if (stored == Long.MIN_VALUE || p.getInt("strict_boot", -2) != bootCount) {
            resetAnchor(); return
        }
        val jump = anchorNow - stored
        if (until > 0 && (jump > 2 * 60_000L || jump < -2 * 60_000L)) {
            // Only matters while locked, or if a forward jump ended the lock early.
            val endedByJump = until <= now && until > now - jump
            if (until > now || endedByJump) {
                until += jump
            }
        }
        p.edit().putLong("strict_anchor", anchorNow).apply()
    }

    fun endLabel(): String = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
        .format(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()))

    fun left(ms: Long = remaining): String {
        val m = ms / 60_000L
        val d = m / (60 * 24)
        val h = (m / 60) % 24
        val mm = m % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${mm}m"
            else -> "${maxOf(mm, 1)}m"
        }
    }

    // ---- uninstall protection
    fun admin(ctx: Context) = ComponentName(ctx, PrimeAdminReceiver::class.java)

    fun adminActive(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(admin(ctx)) == true

    fun adminIntent(ctx: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Prime uses this only so it can't be uninstalled while Strict mode is on. You can remove it from inside Prime once Strict mode ends."
        )

    fun removeAdmin(ctx: Context) {
        if (active) return
        runCatching { ctx.getSystemService(DevicePolicyManager::class.java)?.removeActiveAdmin(admin(ctx)) }
        Store.bump()
    }
}

class PrimeAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Store.init(context)
        return if (Strict.active) "Strict mode is on for ${Strict.left()} more. Prime's protection stays until then."
        else "This removes Prime's uninstall protection."
    }
}

/**
 * Every Settings screen that could switch Prime off, and how it's recognised.
 * Each check gets the screen's package, its activity class (when known) and its visible text, lowercased.
 */
object SettingsMap {

    class Rule(val name: String, val why: String, val test: (pkg: String, cls: String, t: String) -> Boolean)

    val SETTINGS = setOf(
        "com.android.settings",
        "com.google.android.settings",
    )
    val PERMISSION = setOf(
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )
    val INSTALLERS = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.android.vending",
    )
    /** OEM tools that can restrict or kill apps in the background. */
    val OEM_MANAGERS = setOf(
        "com.samsung.android.lool",          // Samsung Device care
        "com.samsung.android.sm",
        "com.miui.securitycenter",           // Xiaomi Security
        "com.coloros.safecenter",            // Oppo / Realme
        "com.oplus.safecenter",
        "com.oplus.battery",
        "com.vivo.permissionmanager",
        "com.iqoo.secure",
        "com.huawei.systemmanager",
        "com.transsion.phonemaster",         // Tecno / Infinix / itel Phone Master
        "com.hihonor.systemmanager",
    )
    val SAMSUNG_A11Y = setOf("com.samsung.accessibility")

    val WATCHED: Set<String> = SETTINGS + PERMISSION + INSTALLERS + OEM_MANAGERS + SAMSUNG_A11Y

    private val PRIME = Regex("\\bprime\\b(?!\\s*video)")
    private fun hasPrime(t: String) = PRIME.containsMatchIn(t)
    private fun String.any(vararg w: String) = w.any { this.contains(it) }

    private val settingsLike = SETTINGS + PERMISSION

    val RULES: List<Rule> = listOf(
        Rule("Accessibility settings", "Where the guard service is switched off") { pkg, cls, t ->
            pkg in SAMSUNG_A11Y ||
                (pkg in settingsLike && cls.contains("accessibility", ignoreCase = true)) ||
                (pkg in settingsLike && hasPrime(t) &&
                    t.any("accessibility", "downloaded apps", "installed apps", "installed services", "use prime", "prime shortcut", "prime sees which app"))
        },
        Rule("Prime's App info", "Force stop, uninstall, clear storage") { pkg, _, t ->
            pkg in settingsLike && hasPrime(t) &&
                t.any("force stop", "uninstall", "clear storage", "clear data", "clear cache", "storage & cache", "storage and cache", "allow restricted settings")
        },
        Rule("Prime's battery & permissions", "Battery restriction, pausing unused apps, revoking access") { pkg, _, t ->
            pkg in settingsLike && hasPrime(t) &&
                t.any("unrestricted", "optimized", "optimised", "restricted", "battery optimization", "battery optimisation",
                    "pause app activity", "remove permissions if app", "manage app if unused", "not allowed", "app battery usage")
        },
        Rule("Device admin apps", "Removing Prime's uninstall protection") { pkg, cls, t ->
            // Turning protection ON stays allowed.
            val activating = t.contains("activate this") && !t.contains("deactivate")
            pkg in settingsLike && !activating && (cls.contains("DeviceAdmin", ignoreCase = true) ||
                (hasPrime(t) && t.any("device admin", "device administrator", "deactivate")))
        },
        Rule("Uninstall screens", "Uninstalling Prime from the home screen or Play Store") { pkg, _, t ->
            pkg in INSTALLERS && hasPrime(t) && t.contains("uninstall")
        },
        Rule("Battery & app managers", "Device care, Phone Master, Security and similar tools, when they show Prime") { pkg, _, t ->
            pkg in OEM_MANAGERS && hasPrime(t)
        },
        Rule("Developer options", "USB and wireless debugging could switch Prime off from a computer") { pkg, cls, t ->
            pkg in settingsLike && (cls.contains("DevelopmentSettings", ignoreCase = true) ||
                t.any("usb debugging", "wireless debugging", "adb authorization", "adb authorisation"))
        },
        Rule("Reset options", "Resetting settings or the phone") { pkg, _, t ->
            pkg in settingsLike &&
                t.any("factory data reset", "erase all data", "reset all settings", "reset accessibility settings",
                    "reset app preferences", "reset settings")
        },
    )

    private val UNINSTALL_PRIME = Regex("uninstall\\s+[\"“']?prime\\b(?!\\s*video)")
    private val UNINSTALL_RULE = Rule("Uninstall screens", "Uninstalling Prime") { _, _, _ -> true }

    /**
     * Uninstall confirmations from any app (installer, launcher, Play Store). Fast enough to run on
     * the first event of a window, from the event's own text, before the dialog can be tapped.
     */
    fun matchUninstall(pkg: String, cls: String, text: String): Rule? {
        val t = text.lowercase()
        val prime = hasPrime(t)
        val uninstallUi = cls.contains("uninstall", ignoreCase = true) || t.contains("uninstall")
        return when {
            UNINSTALL_PRIME.containsMatchIn(t) -> UNINSTALL_RULE
            prime && uninstallUi && (pkg in INSTALLERS || t.contains("do you want to uninstall") ||
                t.contains("want to uninstall") || t.contains("will be uninstalled")) -> UNINSTALL_RULE
            else -> null
        }
    }

    fun match(pkg: String, cls: String, text: String): Rule? {
        matchUninstall(pkg, cls, text)?.let { return it }
        if (pkg !in WATCHED) return null
        val t = text.lowercase()
        return RULES.firstOrNull { it.test(pkg, cls, t) }
    }
}
