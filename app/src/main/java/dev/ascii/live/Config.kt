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
    val highlightColor: Int,
    val bg: Int,
    val ramp: String,
    val duotone: Boolean,
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

        fun resolveHighlight(c: Context, raw: String, primary: Int): Int {
            if (raw == "monet" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return ContextCompat.getColor(c, android.R.color.system_accent2_100)
            }
            val r = (Color.red(primary) + (255 - Color.red(primary)) * 0.58f).toInt().coerceIn(0, 255)
            val g = (Color.green(primary) + (255 - Color.green(primary)) * 0.58f).toInt().coerceIn(0, 255)
            val b = (Color.blue(primary) + (255 - Color.blue(primary)) * 0.58f).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
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
            val primary = resolveAccent(c, colorRaw)
            val highlight = resolveHighlight(c, colorRaw, primary)

            val charsMode = p.getString("chars", "classic") ?: "classic"
            val ramp = when (charsMode) {
                "dense" -> DENSE
                "binary" -> " 01"
                "minimal" -> " .:*#"
                "custom" -> {
                    val rawCustom = p.getString("custom_chars", CLASSIC).orEmpty()
                    val normalized = if (rawCustom.startsWith(' ')) rawCustom else " $rawCustom"
                    if (normalized.length >= 2) normalized else CLASSIC
                }
                else -> CLASSIC
            }

            return Config(
                scene = p.getString("scene", "fusilli") ?: "fusilli",
                cols = p.getInt("cols", 100).coerceIn(40, 200),
                speed = p.getInt("speed", 15).coerceIn(0, 90),
                fps = (p.getString("fps", "60") ?: "60").toIntOrNull() ?: 60,
                thick = p.getInt("thick", 1).coerceIn(1, 3),
                color = primary,
                highlightColor = highlight,
                bg = resolveBg(c, bgRaw),
                ramp = ramp,
                duotone = p.getBoolean("duotone", true),
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
