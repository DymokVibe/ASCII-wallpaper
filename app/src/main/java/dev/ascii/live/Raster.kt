package dev.ascii.live

import kotlin.math.*

private const val DIST = 7f
private const val LX = -0.45f
private const val LY = 0.5f
private const val LZ = -0.74f

/** Z-буфер на сетке символов + простейшая 3D-проекция. Без аллокаций в кадре. */
class Raster {
    var cols = 0
    var rows = 0
    var z = FloatArray(0)
    var l = FloatArray(0)
    var scale = 1f
    var aspect = 0.5f      // ширина ячейки / высота ячейки
    var thick = 1
    var portrait = true
    var userYaw = 0f
    var userPitch = 0f

    private val m = FloatArray(9)
    private var midX = 0f
    private var midY = 0f
    private var px = 0f
    private var py = 0f
    private var pz = 0f

    fun begin(c: Int, r: Int) {
        if (z.size != c * r) { z = FloatArray(c * r); l = FloatArray(c * r) }
        cols = c; rows = r
        midX = c / 2f; midY = r / 2f
        z.fill(Float.MAX_VALUE); l.fill(0f)
    }

    /** M = Rz(roll) * Rx(pitch) * Ry(yaw) */
    fun setRot(yaw: Float, pitch: Float, roll: Float, user: Boolean = true) {
        val y = yaw + if (user) userYaw else 0f
        val p = pitch + if (user) userPitch else 0f
        val cy = cos(y); val sy = sin(y)
        val cp = cos(p); val sp = sin(p)
        val cr = cos(roll); val sr = sin(roll)
        m[0] = cr * cy - sr * sp * sy
        m[1] = -sr * cp
        m[2] = cr * sy + sr * sp * cy
        m[3] = sr * cy + cr * sp * sy
        m[4] = cr * cp
        m[5] = sr * sy - cr * sp * cy
        m[6] = -cp * sy
        m[7] = sp
        m[8] = cp * cy
    }

    private fun proj(x: Float, y: Float, zz: Float): Boolean {
        val rx = m[0] * x + m[1] * y + m[2] * zz
        val ry = m[3] * x + m[4] * y + m[5] * zz
        val rz = m[6] * x + m[7] * y + m[8] * zz
        val d = DIST + rz
        if (d < 1f) return false
        val w = DIST / d
        px = midX + rx * scale * w
        py = midY - ry * scale * w * aspect
        pz = rz
        return true
    }

    private fun put(ix: Int, iy: Int, zv: Float, lum: Float) {
        if (ix < 0 || iy < 0 || ix >= cols || iy >= rows) return
        val i = iy * cols + ix
        if (zv < z[i]) { z[i] = zv; l[i] = lum }
    }

    private fun stamp(gx: Float, gy: Float, zv: Float, lum: Float) {
        val f = (1.25f - 0.2f * zv).coerceIn(0.25f, 1f)
        val v = lum * f
        val ix = floor(gx).toInt()
        val iy = floor(gy).toInt()
        put(ix, iy, zv, v)
        if (thick >= 2) { put(ix + 1, iy, zv, v); put(ix - 1, iy, zv, v) }
        if (thick >= 3) { put(ix, iy + 1, zv, v); put(ix, iy - 1, zv, v) }
    }

    fun pt(x: Float, y: Float, zz: Float, lum: Float) {
        if (proj(x, y, zz)) stamp(px, py, pz, lum)
    }

    /** Точка поверхности с нормалью — освещение по Ламберту. */
    fun pn(x: Float, y: Float, zz: Float, nx: Float, ny: Float, nz: Float, base: Float) {
        if (!proj(x, y, zz)) return
        val rnx = m[0] * nx + m[1] * ny + m[2] * nz
        val rny = m[3] * nx + m[4] * ny + m[5] * nz
        val rnz = m[6] * nx + m[7] * ny + m[8] * nz
        val d = rnx * LX + rny * LY + rnz * LZ
        stamp(px, py, pz, base * (0.15f + 0.85f * max(0f, d)))
    }

    fun line(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, lum: Float) {
        if (!proj(x1, y1, z1)) return
        val ax = px; val ay = py; val az = pz
        if (!proj(x2, y2, z2)) return
        val dx = px - ax; val dy = py - ay
        val n = min(2000, max(abs(dx), abs(dy)).toInt() + 1)
        for (i in 0..n) {
            val t = i / n.toFloat()
            stamp(ax + dx * t, ay + dy * t, az + (pz - az) * t, lum)
        }
    }

    /** Чёрный диск-«заслон» (тень чёрной дыры): закрывает всё, что за ним. */
    fun disc(x: Float, y: Float, zz: Float, rad: Float) {
        if (!proj(x, y, zz)) return
        val rc = rad * scale * (DIST / (DIST + pz))
        val x0 = max(0, (px - rc).toInt()); val x1 = min(cols - 1, (px + rc).toInt() + 1)
        val y0 = max(0, (py - rc * aspect).toInt()); val y1 = min(rows - 1, (py + rc * aspect).toInt() + 1)
        for (iy in y0..y1) for (ix in x0..x1) {
            val dx = ix + 0.5f - px
            val dy = (iy + 0.5f - py) / aspect
            if (dx * dx + dy * dy <= rc * rc) put(ix, iy, pz, 0f)
        }
    }
}
