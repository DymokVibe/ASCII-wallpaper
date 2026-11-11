package dev.ascii.live

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager

private const val CLASSIC = " .:-=+*#%@"
private const val DENSE = " .'`^\",:;Il!i><~+_-?][}{1)(|/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@\$"

data class Config(
    val scene: String, val cols: Int, val speed: Int, val fps: Int, val thick: Int,
    val color: Int, val bg: Int, val ramp: String,
    val tilt: Boolean, val touch: Boolean, val unlock: Boolean, val saver: Boolean
) {
    companion object {
        fun load(c: Context): Config {
            val p = PreferenceManager.getDefaultSharedPreferences(c)
            fun col(k: String, d: String): Int =
                try { Color.parseColor(p.getString(k, d) ?: d) } catch (e: Exception) { Color.parseColor(d) }
            val ramp = when (p.getString("chars", "classic")) {
                "dense" -> DENSE
                "binary" -> " 01"
                "minimal" -> " .:*#"
                else -> CLASSIC
            }
            return Config(
                p.getString("scene", "fusilli") ?: "fusilli",
                p.getInt("cols", 100).coerceIn(40, 200),
                p.getInt("speed", 15),
                (p.getString("fps", "60") ?: "60").toIntOrNull() ?: 60,
                p.getInt("thick", 1).coerceIn(1, 3),
                col("color", "#F2FFF2"), col("bg", "#000000"), ramp,
                p.getBoolean("tilt", false), p.getBoolean("touch", true),
                p.getBoolean("unlock", true), p.getBoolean("saver", true)
            )
        }
    }
}
