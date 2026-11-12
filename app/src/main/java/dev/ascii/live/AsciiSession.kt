package dev.ascii.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RSXform
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import kotlin.math.*

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
    private var pageYaw = 0f
    private var tgtPageYaw = 0f
    private var unlockT = 9f
    private var boostT = 0f
    private var chargeT = 9f

    private var lw = 0
    private var lh = 0
    private var lcols = 0
    private var lcolor = 0
    private var lhiColor = 0
    private var lramp = ""

    var rows = 1
        private set
    private var cellW = 1f
    private var lineH = 1f
    private var aspect = 0.5f
    private var atlasScale = 1f

    private var atlas: Bitmap? = null
    private var srcRects = emptyArray<Rect>()
    private var srcHiRects = emptyArray<Rect>()
    private val dstRect = RectF()
    private var xformBuf: Array<RSXform> = emptyArray()
    private var texBuf: Array<RectF> = emptyArray()

    private var xCoords = FloatArray(0)
    private var yCoords = FloatArray(0)
    private var prevLum = FloatArray(0)

    fun reload() {
        cfg = Config.load(ctx)
    }

    fun unlock() {
        if (cfg.unlock) unlockT = 0f
    }

    fun pulseCharge() {
        chargeT = 0f
        boostT = 1.6f
        yawVel += 2.4f
    }

    fun kick() {
        if (cfg.touch) {
            yawVel += 4.2f
            boostT = 1.6f
        }
    }

    fun drag(dx: Float, dy: Float) {
        if (!cfg.touch) return
        dragYaw += dx * 0.012f
        dragPitch = (dragPitch + dy * 0.012f).coerceIn(-1.2f, 1.2f)
        boostT = 1.2f
    }

    fun pageOffset(xOffset: Float) {
        tgtPageYaw = (xOffset.coerceIn(0f, 1f) - 0.5f) * 1.35f
    }

    fun tilt(ax: Float, az: Float) {
        tgtYaw = (-ax / 9.8f * 0.6f).coerceIn(-0.6f, 0.6f)
        tgtPitch = ((az / 9.8f - 0.35f) * 0.6f).coerceIn(-0.6f, 0.6f)
    }

    fun effectiveFps(lowPower: Boolean): Int {
        if ((cfg.touch && boostT > 0f) || chargeT < 1.2f) return max(cfg.fps, 60)
        return if (cfg.saver && lowPower) min(cfg.fps, 30) else cfg.fps
    }

    private fun step(dt: Float) {
        ang += dt * Math.toRadians(cfg.speed.toDouble()).toFloat()
        if (unlockT < 2f) unlockT += dt
        if (boostT > 0f) boostT = max(0f, boostT - dt)
        if (chargeT < 2f) chargeT += dt
        dragYaw += yawVel * dt
        yawVel *= exp(-1.3f * dt)
        dragPitch *= exp(-1.5f * dt)
        val k = 1f - exp(-7f * dt)
        tiltYaw += ((if (cfg.tilt) tgtYaw else 0f) - tiltYaw) * k
        tiltPitch += ((if (cfg.tilt) tgtPitch else 0f) - tiltPitch) * k
        pageYaw += ((if (cfg.swipe) tgtPageYaw else 0f) - pageYaw) * k
        if (cfg.scene == "cycle") cycleT += dt
    }

    private fun rebuildAtlasAndLayout(w: Int, h: Int) {
        lw = w
        lh = h
        lcols = cfg.cols
        lcolor = cfg.color
        lhiColor = cfg.highlightColor
        lramp = cfg.ramp

        paint.textSize = 100f
        val adv = max(0.1f, paint.measureText("M") / 100f)
        val screenTextSize = w / (cfg.cols * adv)
        paint.textSize = screenTextSize
        val fm = paint.fontMetrics
        val screenAscent = fm.ascent
        lineH = max(1f, fm.descent - fm.ascent)
        cellW = max(1f, w / cfg.cols.toFloat())
        rows = max(1, (h / lineH).toInt())
        val offY = (h - rows * lineH) / 2f
        aspect = cellW / lineH
        r.portrait = h >= w

        xCoords = FloatArray(cfg.cols) { x -> x * cellW }
        yCoords = FloatArray(rows) { y -> offY + y * lineH }

        val totalCells = cfg.cols * rows
        if (prevLum.size != totalCells) {
            prevLum = FloatArray(totalCells)
        } else {
            prevLum.fill(0f)
        }

        val glyphW = max(1, ceil(cellW).toInt())
        atlasScale = cellW / glyphW.toFloat()
        val glyphH = max(1, round(lineH / atlasScale).toInt())
        val rampLen = cfg.ramp.length

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (xformBuf.size != totalCells) {
                xformBuf = Array(totalCells) { RSXform(atlasScale, 0f, 0f, 0f) }
                texBuf = Array(totalCells) { RectF() }
            }
        }

        atlas?.recycle()
        val sheet = Bitmap.createBitmap(glyphW * rampLen, glyphH * 2, Bitmap.Config.ARGB_8888)
        sheet.eraseColor(Color.TRANSPARENT)
        val ac = Canvas(sheet)

        paint.textSize = screenTextSize / atlasScale
        val atlasBaseline = -screenAscent / atlasScale

        paint.color = cfg.color
        val rects = Array(rampLen) { i ->
            val left = i * glyphW
            val ch = cfg.ramp[i]
            if (ch != ' ') {
                ac.drawText(ch.toString(), left.toFloat(), atlasBaseline, paint)
            }
            Rect(left, 0, left + glyphW, glyphH)
        }

        paint.color = cfg.highlightColor
        val rectsHi = Array(rampLen) { i ->
            val left = i * glyphW
            val ch = cfg.ramp[i]
            if (ch != ' ') {
                ac.drawText(ch.toString(), left.toFloat(), glyphH + atlasBaseline, paint)
            }
            Rect(left, glyphH, left + glyphW, glyphH * 2)
        }

        srcRects = rects
        srcHiRects = rectsHi
        atlas = sheet
    }

    fun frame(c: Canvas, w: Int, h: Int, dt: Float) {
        if (w <= 0 || h <= 0) return
        step(dt)
        c.drawColor(cfg.bg)

        if (w != lw || h != lh || cfg.cols != lcols || cfg.color != lcolor ||
            cfg.highlightColor != lhiColor || cfg.ramp != lramp || atlas == null
        ) {
            rebuildAtlasAndLayout(w, h)
        }

        val sheet = atlas ?: return
        val u = if (cfg.unlock) (unlockT / 0.8f).coerceIn(0f, 1f) else 1f
        val ease = 1f - (1f - u) * (1f - u) * (1f - u)

        r.userYaw = dragYaw + tiltYaw + pageYaw + (1f - ease) * 6.2832f
        r.userPitch = dragPitch + tiltPitch
        r.thick = cfg.thick
        r.aspect = aspect

        val idx = if (cfg.scene == "cycle") {
            (cycleT / 20f).toInt() % Scenes.names.size
        } else {
            Scenes.names.indexOf(cfg.scene).coerceAtLeast(0)
        }
        r.begin(cfg.cols, rows)
        r.scale = min(cfg.cols * 0.5f, rows * 0.5f / aspect) / Scenes.extent[idx] * (1f + 0.35f * (1f - ease))
        Scenes.draw(idx, r, ang)

        val n = cfg.ramp.length
        val maxRampIdx = n - 2
        val rampScale = n - 1
        val z = r.z
        val l = r.l
        val cols = cfg.cols
        val useTrail = cfg.trail
        val useDuotone = cfg.duotone
        val decay = if (useTrail && dt > 0f) exp(-11f * dt) else 0f

        val waveActive = chargeT < 1.1f
        val waveRow = rows * (1f - chargeT / 1.1f)
        val waveWidth = max(3f, rows * 0.14f)

        val rects = srcRects
        val rectsHi = srcHiRects

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var count = 0
            val xf = xformBuf
            val tx = texBuf
            val sc = atlasScale
            for (y in 0 until rows) {
                val rowOffset = y * cols
                val y0 = yCoords[y]
                val rowWave = if (waveActive) {
                    val dist = abs(y - waveRow)
                    if (dist < waveWidth) (1f - dist / waveWidth) * 0.92f else 0f
                } else {
                    0f
                }
                for (x in 0 until cols) {
                    val i = rowOffset + x
                    val rawLum = if (z[i] == Float.MAX_VALUE) rowWave else max(l[i], rowWave)
                    val lum = if (useTrail) {
                        val v = max(rawLum, prevLum[i] * decay)
                        prevLum[i] = if (v < 0.015f) 0f else v
                        v
                    } else {
                        rawLum
                    }

                    if (lum >= 0.02f) {
                        val clamped = lum.coerceAtMost(1f)
                        val idxLum = 1 + min((clamped * rampScale).toInt(), maxRampIdx)
                        xf[count].set(sc, 0f, xCoords[x], y0)
                        tx[count].set(if (useDuotone && clamped > 0.80f) rectsHi[idxLum] else rects[idxLum])
                        count++
                    }
                }
            }
            if (count > 0) {
                c.drawAtlas(sheet, xf, tx, null, 0, count, null, null)
            }
        } else {
            val cw = cellW
            val ch = lineH
            for (y in 0 until rows) {
                val rowOffset = y * cols
                val y0 = yCoords[y]
                val y1 = y0 + ch
                val rowWave = if (waveActive) {
                    val dist = abs(y - waveRow)
                    if (dist < waveWidth) (1f - dist / waveWidth) * 0.92f else 0f
                } else {
                    0f
                }
                for (x in 0 until cols) {
                    val i = rowOffset + x
                    val rawLum = if (z[i] == Float.MAX_VALUE) rowWave else max(l[i], rowWave)
                    val lum = if (useTrail) {
                        val v = max(rawLum, prevLum[i] * decay)
                        prevLum[i] = if (v < 0.015f) 0f else v
                        v
                    } else {
                        rawLum
                    }

                    if (lum >= 0.02f) {
                        val clamped = lum.coerceAtMost(1f)
                        val idxLum = 1 + min((clamped * rampScale).toInt(), maxRampIdx)
                        val x0 = xCoords[x]
                        dstRect.set(x0, y0, x0 + cw, y1)
                        c.drawBitmap(sheet, if (useDuotone && clamped > 0.80f) rectsHi[idxLum] else rects[idxLum], dstRect, null)
                    }
                }
            }
        }
    }
}
