package dev.prime.app

import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** On-device image check. Model: nude / nonnude classifier, 224x224 RGB uint8 in, 2 uint8 scores out. */
class NsfwClassifier(ctx: Context) {
    private val interpreter: Interpreter
    private val input = ByteBuffer.allocateDirect(224 * 224 * 3).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(224 * 224)

    init {
        val fd = ctx.assets.openFd("nsfw.tflite")
        val model = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    }

    /** Returns the highest "nude" score across a few square crops of the screen. */
    fun score(screen: Bitmap): Float {
        val w = screen.width
        val h = screen.height
        val crops = mutableListOf<Bitmap>()
        if (h > w * 1.2f) {
            val top = (h * 0.05f).toInt()
            val positions = listOf(top, (h - w) / 2, h - w - (h * 0.04f).toInt())
            positions.distinct().forEach { y ->
                val yy = y.coerceIn(0, h - w)
                crops += Bitmap.createBitmap(screen, 0, yy, w, w)
            }
        } else if (w > h * 1.2f) {
            crops += Bitmap.createBitmap(screen, (w - h) / 2, 0, h, h)
        } else {
            crops += screen
        }
        var best = 0f
        for (c in crops) {
            val s = scoreSquare(c)
            if (s > best) best = s
            if (c !== screen) c.recycle()
        }
        return best
    }

    private fun scoreSquare(b: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(b, 224, 224, true)
        scaled.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        if (scaled !== b) scaled.recycle()
        input.rewind()
        for (px in pixels) {
            input.put(((px shr 16) and 0xFF).toByte())
            input.put(((px shr 8) and 0xFF).toByte())
            input.put((px and 0xFF).toByte())
        }
        input.rewind()
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        val nonnude = (output.get().toInt() and 0xFF) / 256f
        val nude = (output.get().toInt() and 0xFF) / 256f
        return if (nude > nonnude) nude else 0f
    }

    fun close() = interpreter.close()
}

/** Text check against the watch-word list. */
object TextMatcher {
    private var cachedWords: List<String>? = null
    private var regex: Regex? = null

    private fun regexFor(words: List<String>): Regex? {
        if (words == cachedWords) return regex
        cachedWords = words
        val parts = words.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.map { w ->
            val e = Regex.escape(w)
            // Longer words also match as prefixes (e.g. site names inside addresses).
            if (w.length >= 6) "\\b$e" else "\\b$e\\b"
        }
        regex = if (parts.isEmpty()) null else Regex(parts.joinToString("|"))
        return regex
    }

    fun matches(text: String, words: List<String>): Boolean {
        val r = regexFor(words) ?: return false
        return r.containsMatchIn(text.lowercase())
    }

    /** Collect visible text from a window, skipping password fields. Bounded so it stays cheap. */
    fun collect(root: AccessibilityNodeInfo, maxNodes: Int = 700): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < maxNodes) {
            val n = queue.removeFirst()
            seen++
            if (!n.isPassword) {
                n.text?.let { sb.append(it).append(' ') }
                n.contentDescription?.let { sb.append(it).append(' ') }
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return sb.toString()
    }
}
