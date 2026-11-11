package dev.ascii.live

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.*

/** Вся логика кадра. Используется и обоями, и превью в настройках. */
class AsciiSession(private val ctx: Context) {
    var cfg: Config = Config.load(ctx)
        private set

    private val r = Raster()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private var ang = 0f
    private var cycleT = 0f
    private var dragYaw = 0f
    private var yawVel = 0f
    private var dragPitch = 0f
    private var tiltYaw = 0f
    private var tiltPitch = 0f
    private var tgtYaw = 0f
    private var tgtPitch = 0f
    private var unlockT = 9f

    private var lw = 0; private var lh = 0; private var lcols = 0
    private var rows = 1
    private var lineH = 1f; private var ascent = 0f; private var offY = 0f; private var aspect = 0.5f
    private var buf = CharArray(0)

    fun reload() { cfg = Config.load(ctx) }
    fun unlock() { if (cfg.unlock) unlockT = 0f }
    fun kick() { if (cfg.touch) yawVel += 4f }
    fun drag(dx: Float, dy: Float) {
        if (!cfg.touch) return
        dragYaw += dx * 0.012f
        dragPitch = (dragPitch + dy * 0.012f).coerceIn(-1.2f, 1.2f)
    }
    fun tilt(ax: Float, az: Float) {
        tgtYaw = (-ax / 9.8f * 0.6f).coerceIn(-0.6f, 0.6f)
        tgtPitch = ((az / 9.8f - 0.35f) * 0.6f).coerceIn(-0.6f, 0.6f)
    }
    fun effectiveFps(lowPower: Boolean): Int = if (cfg.saver && lowPower) min(cfg.fps, 30) else cfg.fps

    private fun step(dt: Float) {
        ang += dt * Math.toRadians(cfg.speed.toDouble()).toFloat()
        if (unlockT < 2f) unlockT += dt
        dragYaw += yawVel * dt
        yawVel *= exp(-1.3f * dt)
        dragPitch *= exp(-1.5f * dt)
        val k = 1f - exp(-6f * dt)
        tiltYaw += ((if (cfg.tilt) tgtYaw else 0f) - tiltYaw) * k
        tiltPitch += ((if (cfg.tilt) tgtPitch else 0f) - tiltPitch) * k
        if (cfg.scene == "cycle") cycleT += dt
    }

    private fun layout(w: Int, h: Int) {
        lw = w; lh = h; lcols = cfg.cols
        paint.textSize = 100f
        val adv = paint.measureText("M") / 100f
        paint.textSize = w / (cfg.cols * adv)
        val fm = paint.fontMetrics
        ascent = fm.ascent
        lineH = fm.descent - fm.ascent
        rows = max(1, (h / lineH).toInt())
        offY = (h - rows * lineH) / 2f
        aspect = (w / cfg.cols.toFloat()) / lineH
        buf = CharArray(cfg.cols)
        r.portrait = h >= w
    }

    fun frame(c: Canvas, w: Int, h: Int, dt: Float) {
        if (w <= 0 || h <= 0) return
        step(dt)
        c.drawColor(cfg.bg)
        if (w != lw || h != lh || cfg.cols != lcols) layout(w, h)
    
        val u = if (cfg.unlock) (unlockT / 0.8f).coerceIn(0f, 1f) else 1f
        val ease = 1f - (1f - u) * (1f - u) * (1f - u)
        r.userYaw = dragYaw + tiltYaw + (1f - ease) * 6.2832f
        r.userPitch = dragPitch + tiltPitch
        r.thick = cfg.thick
        r.aspect = aspect
    
        val idx = if (cfg.scene == "cycle") (cycleT / 20f).toInt() % Scenes.names.size
                  else Scenes.names.indexOf(cfg.scene).coerceAtLeast(0)
        r.begin(cfg.cols, rows)
        r.scale = min(cfg.cols * 0.5f, rows * 0.5f / aspect) / Scenes.extent[idx] * (1f + 0.35f * (1f - ease))
        Scenes.draw(idx, r, ang)
    
        paint.color = cfg.color
        val ramp = cfg.ramp
        val n = ramp.length
        val maxRampIdx = n - 2
        val rampScale = n - 1
        val z = r.z; val l = r.l
    
        for (y in 0 until rows) {
            var any = false
            val rowOffset = y * cfg.cols
            for (x in 0 until cfg.cols) {
                val i = rowOffset + x
                val lum = l[i]
                if (z[i] == Float.MAX_VALUE || lum < 0.02f) {
                    buf[x] = ' '
                } else {
                    val idxLum = 1 + min((lum.coerceAtMost(1f) * rampScale).toInt(), maxRampIdx)
                    buf[x] = ramp[idxLum]
                    any = true
                }
            }
            if (any) c.drawText(buf, 0, cfg.cols, 0f, offY - ascent + y * lineH, paint)
        }
    }
}
