package dev.ascii.live

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import androidx.preference.PreferenceManager

data class Config(
    val scene: String,
    val cols: Int,
    val fps: Int,
    val speed: Float,
    val thick: Int,
    val colorPreset: String,
    val color: Int,
    val bg: Int,
    val ramp: String,
    val tilt: Boolean,
    val touch: Boolean,
    val unlock: Boolean,
    val saver: Boolean
) {
    fun getEffectiveColor(ctx: Context): Int {
        return when (colorPreset) {
            "matrix" -> Color.parseColor("#00FF66")
            "cyberpunk" -> Color.parseColor("#00E5FF")
            "amber" -> Color.parseColor("#FFB000")
            "monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getColor(android.R.color.system_accent1_300)
            } else {
                Color.parseColor("#00FF66")
            }
            else -> color
        }
    }

    companion object {
        private fun getIntSafe(p: SharedPreferences, key: String, defaultVal: Int): Int {
            return try {
                p.getInt(key, defaultVal)
            } catch (e: Exception) {
                p.getString(key, null)?.toIntOrNull() ?: defaultVal
            }
        }

        private fun getFloatSafe(p: SharedPreferences, key: String, defaultVal: Float): Float {
            return try {
                p.getInt(key, defaultVal.toInt()).toFloat()
            } catch (e: Exception) {
                p.getString(key, null)?.toFloatOrNull() ?: defaultVal
            }
        }

        private fun parseColorSafe(p: SharedPreferences, key: String, defaultHex: String): Int {
            val defaultColor = Color.parseColor(defaultHex)
            return try {
                val str = p.getString(key, null)
                if (!str.isNullOrEmpty()) Color.parseColor(str) else defaultColor
            } catch (e: Exception) {
                try {
                    p.getInt(key, defaultColor)
                } catch (e2: Exception) {
                    defaultColor
                }
            }
        }

        fun load(ctx: Context): Config {
            val p = PreferenceManager.getDefaultSharedPreferences(ctx)

            val rampKey = try { p.getString("chars", "classic") ?: "classic" } catch (e: Exception) { "classic" }
            val rampStr = when (rampKey) {
                "minimal" -> " .:-=+#@"
                "dense" -> " .'`^\":;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$"
                else -> " .:-=+*#%@"
            }

            return Config(
                scene = try { p.getString("scene", "fusilli") ?: "fusilli" } catch (e: Exception) { "fusilli" },
                cols = getIntSafe(p, "cols", 80),
                fps = getIntSafe(p, "fps", 30),
                speed = getFloatSafe(p, "speed", 15f),
                thick = getIntSafe(p, "thick", 1),
                colorPreset = try { p.getString("color_preset", "matrix") ?: "matrix" } catch (e: Exception) { "matrix" },
                color = parseColorSafe(p, "color", "#F2FFF2"),
                bg = parseColorSafe(p, "bg", "#000000"),
                ramp = rampStr,
                tilt = try { p.getBoolean("tilt", false) } catch (e: Exception) { false },
                touch = try { p.getBoolean("touch", true) } catch (e: Exception) { true },
                unlock = try { p.getBoolean("unlock", true) } catch (e: Exception) { true },
                saver = try { p.getBoolean("saver", true) } catch (e: Exception) { true }
            )
        }
    }
}
