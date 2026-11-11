package dev.ascii.live

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val m = (16 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF050A07.toInt())
            fitsSystemWindows = true
        }
        root.addView(PreviewView(this), LinearLayout.LayoutParams(MATCH_PARENT, (220 * d).toInt()).apply {
            setMargins(m, m, m, m / 2)
        })
        root.addView(AppCompatButton(this).apply {
            text = "Установить обои  →"
            setTextColor(Color.BLACK)
            backgroundTintList = ColorStateList.valueOf(0xFF6BFF7A.toInt())
            setOnClickListener { install() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(m, 0, m, 0) })
        val frame = FrameLayout(this).apply { id = R.id.settings_container }
        root.addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settings_container, Prefs()).commit()
        }
    }

    private fun install() {
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, AsciiWallpaperService::class.java)
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    class Prefs : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
        }
    }
}
