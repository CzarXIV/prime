package dev.prime.app

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

const val ACCENT_ARGB = 0xFFFF3B1F.toInt()

fun setMode(ctx: Context, m: Mode) {
    Store.mode = m
    if (Store.wallpaperEnabled) PrimeWallpaper.apply(ctx)
}

fun a11yEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val me = ComponentName(ctx, PrimeAccessibilityService::class.java)
    return s.split(':').any { ComponentName.unflattenFromString(it) == me }
}

fun goHome(ctx: Context) {
    ctx.startActivity(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

object PrimeWallpaper {
    fun apply(ctx: Context) {
        val app = ctx.applicationContext
        val mode = Store.mode
        val intention = Store.today().intention
        Thread {
            runCatching {
                val dm = app.resources.displayMetrics
                val w = dm.widthPixels
                val h = dm.heightPixels
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.BLACK)
                val heavy = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 900, false)
                else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                val left = w * 0.08f
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = heavy }

                var y = h * 0.30f
                p.color = ACCENT_ARGB
                c.drawRect(left, y, left + w * 0.14f, y + w * 0.022f, p)

                p.color = Color.WHITE
                p.textSize = w * 0.16f
                y += w * 0.20f
                for (line in mode.label.split(" ")) {
                    c.drawText(line, left, y, p)
                    y += p.textSize * 0.98f
                }

                p.color = 0xFF8C8C8C.toInt()
                p.textSize = w * 0.04f
                p.letterSpacing = 0.15f
                c.drawText(mode.tagline.uppercase(), left, y + w * 0.02f, p)

                if (intention.isNotBlank()) {
                    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = heavy
                        color = ACCENT_ARGB
                        textSize = w * 0.055f
                    }
                    val width = (w - left * 2).toInt()
                    val layout = StaticLayout.Builder.obtain(intention, 0, intention.length, tp, width)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setMaxLines(4)
                        .build()
                    c.save()
                    c.translate(left, y + w * 0.12f)
                    layout.draw(c)
                    c.restore()
                }

                WallpaperManager.getInstance(app).setBitmap(
                    bmp, null, true,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                )
            }
        }.start()
    }
}

object Reminders {
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        schedule(ctx, am, 1, Store.morningHour, "morning")
        schedule(ctx, am, 2, Store.eveningHour, "evening")
    }

    private fun schedule(ctx: Context, am: AlarmManager, id: Int, hour: Int, kind: String) {
        val pi = PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, RitualReceiver::class.java).putExtra("kind", kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }
}

class RitualReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        val kind = intent.getStringExtra("kind") ?: return
        val day = Store.today()
        if (kind == "morning" && day.morning) return
        if (kind == "evening" && day.evening) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder("rituals", NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Rituals")
                .build()
        )
        val morning = kind == "morning"
        val open = PendingIntent.getActivity(
            ctx, if (morning) 11 else 12,
            Intent(ctx, MainActivity::class.java)
                .putExtra("open", kind)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (morning) "PRIME YOUR DAY" else "CLOSE THE DAY"
        val text = if (morning) "One intention. Three tasks. Two minutes."
        else "Review it before you sleep. Protect the streak."
        val n = NotificationCompat.Builder(ctx, "rituals")
            .setSmallIcon(R.drawable.ic_prime)
            .setColor(ACCENT_ARGB)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(if (morning) 1 else 2, n)
        } catch (_: SecurityException) {
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(ctx)
        Reminders.schedule(ctx)
    }
}
