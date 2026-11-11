package dev.ascii.live

import kotlin.math.*

private const val TAU = 6.2831855f

object Scenes {
    val names = arrayOf("fusilli", "cubes", "bigcube", "dna", "blackhole", "matrix")
    val extent = floatArrayOf(2.0f, 1.7f, 2.4f, 2.0f, 5.4f, 2.0f)

    fun draw(i: Int, r: Raster, a: Float) {
        when (i) {
            0 -> fusilli(r, a)
            1 -> cubes(r, a)
            2 -> { r.setRot(0f, 0f, 0f); cube(r, 0f, 0f, 0f, 1.3f, a, a * 0.6f) }
            3 -> dna(r, a)
            4 -> blackHole(r, a)
            else -> matrixRain(r, a)
        }
    }
    // ---------- Макароны: две спиральные «трубки», точки с нормалями считаются один раз ----------
    private val fus: FloatArray by lazy {
        val rods = 2; val n = 260; val m = 14
        val out = FloatArray(rods * n * m * 6)
        var k = 0
        for (rod in 0 until rods) {
            val ox = if (rod == 0) -0.95f else 0.95f
            val rz = if (rod == 0) 0.3f else -0.3f
            val crz = cos(rz); val srz = sin(rz)
            val phase = rod * 3.1415927f
            for (i in 0 until n) {
                val u = i / (n - 1f)
                val y = (u - 0.5f) * 3.4f
                val ph = u * TAU * 3.2f + phase
                val ox2 = cos(ph); val oz = sin(ph)
                for (j in 0 until m) {
                    val v = j * TAU / m
                    val nx = cos(v) * ox2; val ny = sin(v); val nz = cos(v) * oz
                    val px = 0.42f * ox2 + 0.2f * nx
                    val py = y + 0.2f * ny
                    val pz = 0.42f * oz + 0.2f * nz
                    out[k++] = px * crz - py * srz + ox
                    out[k++] = px * srz + py * crz
                    out[k++] = pz
                    out[k++] = nx * crz - ny * srz
                    out[k++] = nx * srz + ny * crz
                    out[k++] = nz
                }
            }
        }
        out
    }

    private fun fusilli(r: Raster, a: Float) {
        r.setRot(a, 0.35f, 0f)
        val d = fus
        var k = 0
        while (k < d.size) {
            r.pn(d[k], d[k + 1], d[k + 2], d[k + 3], d[k + 4], d[k + 5], 1f)
            k += 6
        }
    }

    // ---------- Кубы ----------
    private val ev = FloatArray(24)

    private fun cube(r: Raster, cx: Float, cy: Float, cz: Float, s: Float, a: Float, b: Float) {
        val ca = cos(a); val sa = sin(a); val cb = cos(b); val sb = sin(b)
        for (i in 0 until 8) {
            val x = ((i and 1) * 2 - 1) * s
            val y = (((i shr 1) and 1) * 2 - 1) * s
            val z = ((i shr 2) * 2 - 1) * s
            val x1 = x * ca + z * sa
            val z1 = -x * sa + z * ca
            val y1 = y * cb - z1 * sb
            val z2 = y * sb + z1 * cb
            ev[i * 3] = x1 + cx; ev[i * 3 + 1] = y1 + cy; ev[i * 3 + 2] = z2 + cz
        }
        for (i in 0 until 8) for (bi in 0..2) {
            val bit = 1 shl bi
            if ((i and bit) == 0) {
                val j = i or bit
                r.line(ev[i * 3], ev[i * 3 + 1], ev[i * 3 + 2], ev[j * 3], ev[j * 3 + 1], ev[j * 3 + 2], 1f)
            }
        }
    }

    private fun cubes(r: Raster, a: Float) {
        r.setRot(0f, 0f, 0f)
        val sx = if (r.portrait) 0f else 1.35f
        val sy = if (r.portrait) 1.35f else 0f
        cube(r, -sx, -sy, 0f, 0.8f, a, a * 0.7f)
        cube(r, sx, sy, 0f, 0.8f, -a * 0.9f, a * 0.5f + 1f)
    }

    // ---------- ДНК: бесконечная диагональ ----------
    private fun dna(r: Raster, a: Float) {
        r.setRot(0f, 0f, -0.6f)
        val n = 150; val y0 = -8f; val dy = 16f / n
        var pax = 0f; var paz = 0f; var pbx = 0f; var pbz = 0f
        for (i in 0..n) {
            val y = y0 + i * dy
            val ph = y * 1.5f + a * 2.2f
            val ax = cos(ph); val az = sin(ph)
            val bx = -ax; val bz = -az
            if (i > 0) {
                r.line(pax, y - dy, paz, ax, y, az, 1f)
                r.line(pbx, y - dy, pbz, bx, y, bz, 1f)
            }
            if (i % 4 == 0) r.line(ax, y, az, bx, y, bz, 0.55f)
            pax = ax; paz = az; pbx = bx; pbz = bz
        }
    }

    // ---------- Чёрная дыра ----------
 // Храним 4 значения на точку: [rr, initial_th, speed_factor, base_lum]
 private val disk: FloatArray by lazy {
     val list = ArrayList<Float>()
     var rr = 1.9f
     while (rr < 5.2f) {
         val cnt = (rr * 60).toInt()
         val speed = 5f / (rr * sqrt(rr)) // Предрассчитано
         val baseLum = (2f / rr).pow(1.2f) // Предрассчитано
         for (i in 0 until cnt) {
             val th = i * TAU / cnt + rr * 7.13f
             list.add(rr)
             list.add(th)
             list.add(speed)
             list.add(baseLum)
         }
         rr += 0.06f
     }
     list.toFloatArray()
 }
 
 private fun blackHole(r: Raster, a: Float) {
     r.setRot(0f, 0f, 0f, false)
     r.disc(0f, 0f, 0f, 1.45f)
     for (k in 0 until 3) {
         val rad = 1.62f + k * 0.17f
         val cnt = (rad * 90).toInt()
         for (i in 0 until cnt) {
             val ph = i * TAU / cnt
             r.pt(rad * cos(ph), rad * sin(ph), -0.3f, (0.95f - k * 0.25f) * (0.7f + 0.3f * sin(ph * 2f + a * 3f)))
         }
     }
     r.setRot(0f, -0.28f, 0f)
     val d = disk
     var k = 0
     while (k < d.size) {
         val rr = d[k]
         val th = d[k + 1] + a * d[k + 2]
         val lum = d[k + 3] * (0.75f + 0.25f * cos(th))
         r.pt(rr * cos(th), 0f, rr * sin(th), lum)
         k += 4
     }
 }

private fun matrixRain(r: Raster, a: Float) {
        r.setRot(0f, 0f, 0f, false)
        val cols = r.cols
        val rows = r.rows
        if (cols <= 0 || rows <= 0) return

        val midX = cols / 2f
        val midY = rows / 2f
        val invScaleX = 1f / (r.scale)
        val invScaleY = 1f / (r.scale * r.aspect)

        for (x in 0 until cols) {
            // Псевдослучайный хэш для каждой колонки
            val seed = (x * 12.9898f).toBits() and 0x7FFFFFFF
            val rnd1 = (seed % 1000) / 1000f
            val rnd2 = ((seed / 1000) % 1000) / 1000f

            val speed = 6f + rnd1 * 14f
            val tailLen = 7f + rnd2 * 12f
            val headY = ((a * speed + rnd1 * 100f) % (rows + tailLen * 2)) - tailLen

            for (yStep in 0 until tailLen.toInt()) {
                val iy = (headY - yStep).toInt()
                if (iy in 0 until rows) {
                    val lum = if (yStep == 0) 1.0f else (1.0f - yStep / tailLen).pow(1.4f) * 0.8f
                    val rx = (x - midX) * invScaleX
                    val ry = (midY - iy) * invScaleY
                    r.pt(rx, ry, 0f, lum)
                }
            }
        }
    }
}
