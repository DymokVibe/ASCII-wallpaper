package dev.ascii.live

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Outline
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import androidx.preference.PreferenceManager

/** Превью в настройках: тот же рендерер, что и у обоев, ~30 FPS. */
class PreviewView(c: Context) : View(c) {
    private val session = AsciiSession(c.applicationContext)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(c.applicationContext)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> session.reload() }
    private var last = 0L
    private var tx = 0f
    private var ty = 0f

    init {
        val radius = 16 * resources.displayMetrics.density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) }
        }
        clipToOutline = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session.reload()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        last = 0L
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
        postInvalidateDelayed(33)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { tx = e.x; ty = e.y; parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> { session.drag(e.x - tx, e.y - ty); tx = e.x; ty = e.y }
        }
        return true
    }
}
