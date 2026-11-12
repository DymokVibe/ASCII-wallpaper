package dev.ascii.live

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Outline
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import androidx.preference.PreferenceManager
import java.util.Locale

class PreviewView(c: Context) : View(c) {
    private val session = AsciiSession(c.applicationContext)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(c.applicationContext)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        session.reload()
        invalidate()
    }

    var onStats: ((fps: Int, cols: Int, rows: Int, hex: String) -> Unit)? = null

    private var last = 0L
    private var tx = 0f
    private var ty = 0f
    private var frames = 0
    private var statsTs = 0L
    private var measuredFps = 60

    init {
        val radius = 18 * resources.displayMetrics.density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        clipToOutline = true
    }

    fun reloadNow() {
        session.reload()
        invalidate()
    }

    fun triggerChargeDemo() {
        session.pulseCharge()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session.reload()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        last = 0L
        statsTs = System.nanoTime()
        frames = 0
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
        last = now

        session.frame(canvas, width, height, dt)

        frames++
        val elapsedStats = now - statsTs
        if (elapsedStats >= 450_000_000L) {
            measuredFps = ((frames * 1_000_000_000L) / elapsedStats).toInt().coerceIn(1, 120)
            frames = 0
            statsTs = now
            val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and session.cfg.color)
            onStats?.invoke(measuredFps, session.cfg.cols, session.rows, hex)
        }

        val targetInterval = (1000L / session.effectiveFps(false).coerceIn(30, 60)).coerceAtLeast(16L)
        postInvalidateDelayed(targetInterval)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tx = e.x
                ty = e.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                session.drag(e.x - tx, e.y - ty)
                tx = e.x
                ty = e.y
            }
            MotionEvent.ACTION_UP -> {
                session.kick()
            }
        }
        return true
    }
}
