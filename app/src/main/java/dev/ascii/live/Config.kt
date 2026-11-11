package dev.ascii.live

import android.content.Context
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
        fun load(ctx: Context): Config {
            val p = PreferenceManager.getDefaultSharedPreferences(ctx)
            return Config(
                scene = p.getString("scene", "fusilli") ?: "fusilli",
                cols = p.getString("cols", "80")?.toIntOrNull() ?: 80,
                fps = p.getString("fps", "30")?.toIntOrNull() ?: 30,
                speed = p.getString("speed", "1.0")?.toFloatOrNull() ?: 1.0f,
                thick = p.getString("thick", "1")?.toIntOrNull() ?: 1,
                colorPreset = p.getString("color_preset", "matrix") ?: "matrix",
                color = p.getInt("color", Color.GREEN),
                bg = p.getInt("bg", Color.BLACK),
                ramp = p.getString("ramp", " .:-=+*#%@") ?: " .:-=+*#%@",
                tilt = p.getBoolean("tilt", true),
                touch = p.getBoolean("touch", true),
                unlock = p.getBoolean("unlock", true),
                saver = p.getBoolean("saver", true)
            )
        }
    }
}
