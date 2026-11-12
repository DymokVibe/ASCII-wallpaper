package dev.ascii.live

import kotlin.math.*

private const val TAU = 6.2831855f

object Scenes {
    val names = arrayOf("fusilli", "cubes", "bigcube", "dna", "blackhole", "torus", "sphere")
    val extent = floatArrayOf(2.0f, 1.7f, 2.4f, 2.0f, 5.4f, 2.2f, 2.0f)

    fun draw(i: Int, r: Raster, a: Float) {
        when (i) {
            0 -> fusilli(r, a)
            1 -> cubes(r, a)
            2 -> {
                r.setRot(0f, 0f, 0f)
                cube(r, 0f, 0f, 0f, 1.3f, a, a * 0.6f)
            }
            3 -> dna(r, a)
            4 -> blackHole(r, a)
            5 -> torus(r, a)
            else -> sphere(r, a)
        }
    }

    // ---------- Макароны ----------
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

    // ---------- ДНК ----------
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

    // ---------- Чёрная дыра (кольцевой батчинг без sin/cos на частицу) ----------
    private class DiskRing(
        val rr: Float,
        val speed: Float,
        val baseLum: Float,
        val cosSin: FloatArray
    )

    private val diskRings: Array<DiskRing> by lazy {
        val rings = ArrayList<DiskRing>()
        var rr = 1.9f
        while (rr < 5.2f) {
            val cnt = (rr * 60).toInt()
            val speed = 5f / (rr * sqrt(rr))
            val baseLum = (2f / rr).pow(1.2f)
            val cs = FloatArray(cnt * 2)
            for (i in 0 until cnt) {
                val th = i * TAU / cnt + rr * 7.13f
                cs[i * 2] = cos(th)
                cs[i * 2 + 1] = sin(th)
            }
            rings.add(DiskRing(rr, speed, baseLum, cs))
            rr += 0.06f
        }
        rings.toTypedArray()
    }

    private fun blackHole(r: Raster, a: Float) {
        r.setRot(0f, 0f, 0f, false)
        r.disc(0f, 0f, 0f, 1.45f)
        for (k in 0 until 3) {
            val rad = 1.62f + k * 0.17f
            val cnt = (rad * 90).toInt()
            val step = TAU / cnt
            val cStep = cos(step)
            val sStep = sin(step)
            var cPh = 1f
            var sPh = 0f
            val baseBright = 0.95f - k * 0.25f
            for (i in 0 until cnt) {
                val ph = i * step
                val lum = baseBright * (0.7f + 0.3f * sin(ph * 2f + a * 3f))
                r.pt(rad * cPh, rad * sPh, -0.3f, lum)
                val nextC = cPh * cStep - sPh * sStep
                sPh = sPh * cStep + cPh * sStep
                cPh = nextC
            }
        }
        r.setRot(0f, -0.28f, 0f)
        val rings = diskRings
        for (ri in rings.indices) {
            val ring = rings[ri]
            val dth = a * ring.speed
            val cd = cos(dth)
            val sd = sin(dth)
            val rr = ring.rr
            val bLum = ring.baseLum
            val cs = ring.cosSin
            var k = 0
            while (k < cs.size) {
                val c0 = cs[k]
                val s0 = cs[k + 1]
                val cth = c0 * cd - s0 * sd
                val sth = s0 * cd + c0 * sd
                r.pt(rr * cth, 0f, rr * sth, bLum * (0.75f + 0.25f * cth))
                k += 2
            }
        }
    }

    // ---------- Тор (Бублик) ----------
    private val tor: FloatArray by lazy {
        val n = 72
        val m = 30
        val majorR = 1.25f
        val minorR = 0.54f
        val out = FloatArray(n * m * 6)
        var k = 0
        for (i in 0 until n) {
            val u = i * TAU / n
            val cu = cos(u); val su = sin(u)
            for (j in 0 until m) {
                val v = j * TAU / m
                val cv = cos(v); val sv = sin(v)
                val nx = cv * cu
                val ny = sv
                val nz = cv * su
                val rr = majorR + minorR * cv
                out[k++] = rr * cu
                out[k++] = minorR * sv
                out[k++] = rr * su
                out[k++] = nx
                out[k++] = ny
                out[k++] = nz
            }
        }
        out
    }

    private fun torus(r: Raster, a: Float) {
        r.setRot(a * 0.85f, 0.58f, a * 0.35f)
        val d = tor
        var k = 0
        while (k < d.size) {
            r.pn(d[k], d[k + 1], d[k + 2], d[k + 3], d[k + 4], d[k + 5], 1f)
            k += 6
        }
    }

    // ---------- Сфера Фибоначчи с гармонической пульсацией ----------
    private val fibSphere: FloatArray by lazy {
        val count = 960
        val goldenAngle = 2.3999632f
        val out = FloatArray(count * 4) // [x, y, z, azimuth]
        var k = 0
        for (i in 0 until count) {
            val y = 1f - (i / (count - 1f)) * 2f
            val rad = sqrt(max(0f, 1f - y * y))
            val theta = goldenAngle * i
            out[k++] = cos(theta) * rad
            out[k++] = y
            out[k++] = sin(theta) * rad
            out[k++] = theta
        }
        out
    }

    private fun sphere(r: Raster, a: Float) {
        r.setRot(a * 0.7f, 0.35f, a * 0.25f)
        val d = fibSphere
        var k = 0
        while (k < d.size) {
            val nx = d[k]
            val ny = d[k + 1]
            val nz = d[k + 2]
            val az = d[k + 3]
            val wave = sin(ny * 4.5f + a * 2.6f) * cos(az * 3f - a * 1.9f)
            val rad = 1.38f + 0.22f * wave
            val lum = (0.78f + 0.22f * wave).coerceIn(0.35f, 1f)
            r.pn(nx * rad, ny * rad, nz * rad, nx, ny, nz, lum)
            k += 4
        }
    }
}
