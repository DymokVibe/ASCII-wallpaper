package dev.ascii.live

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

private const val CLASSIC = " .:-=+*#%@"
private const val DENSE = " .'`^\",:;Il!i><~+_-?][}{1)(|/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@\$"

data class Config(
    val scene: String,
    val cols: Int,
    val speed: Int,
    val fps: Int,
    val thick: Int,
    val color: Int,
    val bg: Int,
    val ramp: String,
    val trail: Boolean,
    val swipe: Boolean,
    val tilt: Boolean,
    val touch: Boolean,
    val unlock: Boolean,
    val saver: Boolean
) {
    companion object {
        fun resolveAccent(c: Context, raw: String): Int {
            if (raw == "monet") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val night = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                    val resId = if (night) {
                        android.R.color.system_accent1_200
                    } else {
                        android.R.color.system_accent1_300
                    }
                    return ContextCompat.getColor(c, resId)
                }
                return 0xFF6BFF7A.toInt()
            }
            return try {
                Color.parseColor(raw)
            } catch (_: Exception) {
                0xFF6BFF7A.toInt()
            }
        }

        fun resolveBg(c: Context, raw: String): Int {
            if (raw == "monet_bg") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return ContextCompat.getColor(c, android.R.color.system_neutral1_900)
                }
                return 0xFF050A07.toInt()
            }
            return try {
                Color.parseColor(raw)
            } catch (_: Exception) {
                Color.BLACK
            }
        }

        fun load(c: Context): Config {
            val p = PreferenceManager.getDefaultSharedPreferences(c)
            val colorRaw = p.getString("color", "monet") ?: "monet"
            val bgRaw = p.getString("bg", "#000000") ?: "#000000"
            val ramp = when (p.getString("chars", "classic")) {
                "dense" -> DENSE
                "binary" -> " 01"
                "minimal" -> " .:*#"
                else -> CLASSIC
            }
            return Config(
                scene = p.getString("scene", "fusilli") ?: "fusilli",
                cols = p.getInt("cols", 100).coerceIn(40, 200),
                speed = p.getInt("speed", 15).coerceIn(0, 90),
                fps = (p.getString("fps", "60") ?: "60").toIntOrNull() ?: 60,
                thick = p.getInt("thick", 1).coerceIn(1, 3),
                color = resolveAccent(c, colorRaw),
                bg = resolveBg(c, bgRaw),
                ramp = ramp,
                trail = p.getBoolean("trail", true),
                swipe = p.getBoolean("swipe", true),
                tilt = p.getBoolean("tilt", false),
                touch = p.getBoolean("touch", true),
                unlock = p.getBoolean("unlock", true),
                saver = p.getBoolean("saver", true)
            )
        }
    }
}
