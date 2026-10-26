package dev.prime.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * The guard. It:
 *  - shows the friction screen before apps on the distraction list (Deep Work / Learning)
 *  - shows the intention screen after the phone is unlocked
 *  - content guard: checks on-screen text for watch words, and once a minute checks a
 *    screenshot with an on-device classifier. Nothing is stored or sent anywhere.
 */
class PrimeAccessibilityService : AccessibilityService() {

    private var unlockReceiver: BroadcastReceiver? = null
    private var lastPkg: String? = null
    private var lastLaunch = 0L

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var classifier: NsfwClassifier? = null

    private var scanPending = false
    private var lastScan = 0L
    private var banner: View? = null

    private val SKIP = setOf("com.android.systemui")

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        val caps = serviceInfo?.capabilities ?: 0
        Train.capText = caps and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
        Train.capShot = Build.VERSION.SDK_INT >= 30 &&
            caps and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
        Store.bump()

        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_USER_PRESENT) onUnlock()
            }
        }
        ContextCompat.registerReceiver(
            this, r, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED
        )
        unlockReceiver = r
        main.postDelayed(imageTick, 60_000L)
    }

    // ------------------------------------------------------------ unlock

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

    // ------------------------------------------------------------ events

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            appFriction(pkg)
        }
        if (Train.guardOn && Train.capText && pkg !in SKIP) {
            scheduleTextScan()
        }
    }

    private fun appFriction(pkg: String) {
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

    // ------------------------------------------------------------ text check

    private fun scheduleTextScan() {
        if (scanPending) return
        scanPending = true
        val wait = maxOf(600L, 1500L - (SystemClock.elapsedRealtime() - lastScan))
        main.postDelayed({
            scanPending = false
            lastScan = SystemClock.elapsedRealtime()
            textScan()
        }, wait)
    }

    private fun textScan() {
        if (!Train.guardOn || isQuiet()) return
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg == packageName || pkg in SKIP) return
        val words = Train.words
        worker.execute {
            val text = runCatching { TextMatcher.collect(root) }.getOrDefault("")
            if (TextMatcher.matches(text, words)) main.post { catchMoment(pkg, "text") }
        }
    }

    // ------------------------------------------------------------ image check (once a minute)

    private val imageTick = object : Runnable {
        override fun run() {
            runCatching { imageCheck() }
            main.postDelayed(this, 60_000L)
        }
    }

    private fun imageCheck() {
        if (Build.VERSION.SDK_INT < 30) return
        if (!Train.guardOn || !Train.imageOn || !Train.capShot || isQuiet()) return
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        if (pm?.isInteractive != true || km?.isKeyguardLocked == true) return
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        if (pkg == packageName || pkg in SKIP) return

        takeScreenshot(Display.DEFAULT_DISPLAY, worker, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hb = result.hardwareBuffer
                val shot = runCatching {
                    Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.let { hw ->
                        val sw = hw.copy(Bitmap.Config.ARGB_8888, false)
                        hw.recycle()
                        sw
                    }
                }.getOrNull()
                hb.close()
                if (shot == null) return
                val score = runCatching {
                    val c = classifier ?: NsfwClassifier(this@PrimeAccessibilityService).also { classifier = it }
                    c.score(shot)
                }.getOrDefault(0f)
                shot.recycle()
                if (score >= Train.threshold) main.post { catchMoment(pkg, "image") }
            }

            override fun onFailure(errorCode: Int) {}
        })
    }

    // ------------------------------------------------------------ the moment

    private fun isQuiet() = System.currentTimeMillis() < Train.quietUntil

    private fun catchMoment(pkg: String, src: String) {
        if (isQuiet()) return
        Train.quietUntil = System.currentTimeMillis() + 90_000L
        Train.evaluate()
        when (Train.level) {
            0, 1 -> startActivity(
                Intent(this, if (Train.level == 0) ContentPauseActivity::class.java else ContentNudgeActivity::class.java)
                    .putExtra("pkg", pkg)
                    .putExtra("src", src)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            else -> showBanner(pkg, src)
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun showBanner(pkg: String, src: String) {
        removeBanner()
        val wm = getSystemService(WindowManager::class.java) ?: return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(0, 0, dp(8f), 0)
        }
        val bar = View(this).apply { setBackgroundColor(ACCENT_ARGB) }
        row.addView(bar, LinearLayout.LayoutParams(dp(5f), dp(56f)))
        val msg = TextView(this).apply {
            text = "PRIME NOTICED. YOUR CALL."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setPadding(dp(14f), 0, dp(8f), 0)
        }
        row.addView(msg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btn = TextView(this).apply {
            text = "WALK AWAY"
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(ACCENT_ARGB)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.15f
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            setOnClickListener {
                Train.record(true, pkg, src)
                removeBanner()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        row.addView(btn)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(36f)
            x = 0
        }
        runCatching { wm.addView(row, lp) }.onSuccess {
            banner = row
            main.postDelayed({ if (banner === row) removeBanner() }, 6000L)
        }
    }

    private fun removeBanner() {
        val b = banner ?: return
        banner = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(b) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        removeBanner()
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
        worker.execute { runCatching { classifier?.close() } }
        worker.shutdown()
        super.onDestroy()
    }
}
