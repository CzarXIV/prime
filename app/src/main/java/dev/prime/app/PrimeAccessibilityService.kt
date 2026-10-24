package dev.prime.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * The guard. Listens for which app comes to the foreground (nothing else) and:
 *  - shows the friction screen before apps on the distraction list, in Deep Work / Learning
 *  - shows the intention screen after the phone is unlocked
 */
class PrimeAccessibilityService : AccessibilityService() {

    private var unlockReceiver: BroadcastReceiver? = null
    private var lastPkg: String? = null
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_USER_PRESENT) onUnlock()
            }
        }
        ContextCompat.registerReceiver(
            this, r, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED
        )
        unlockReceiver = r
    }

    private fun onUnlock() {
        if (!Store.unlockEnabled) return
        val now = System.currentTimeMillis()
        if (now - Store.lastUnlockShown < Store.unlockCooldownMin * 60_000L) return
        Store.lastUnlockShown = now
        startActivity(
            Intent(this, PrimeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!Store.mode.friction) return
        if (pkg !in Store.blocked) return
        if (System.currentTimeMillis() < Store.passUntil(pkg)) return

        val t = SystemClock.elapsedRealtime()
        if (pkg == lastPkg && t - lastLaunch < 1500) return
        lastPkg = pkg
        lastLaunch = t

        startActivity(
            Intent(this, FrictionActivity::class.java)
                .putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
        super.onDestroy()
    }
}
