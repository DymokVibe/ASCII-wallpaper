package dev.ascii.live

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var previewView: PreviewView
    private lateinit var previewFrame: FrameLayout
    private lateinit var hudBadge: TextView
    private lateinit var statusPill: TextView
    private lateinit var applyBtn: TextView

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        syncAccentUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val d = resources.displayMetrics.density
        val m = (16 * d).toInt()
        val s = (8 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF070A0D.toInt())
            fitsSystemWindows = true
        }

        // Верхняя панель: заголовок и статус активности обоев
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(m, (12 * d).toInt(), m, s)
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBlock.addView(TextView(this).apply {
            text = "ASCII LIVE"
            setTextColor(0xFFF2FFF2.toInt())
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.08f
        })
        titleBlock.addView(TextView(this).apply {
            text = "2D ASCII Engine · Monet"
            setTextColor(0xFF7D8994.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
        })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        statusPill = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
        }
        header.addView(statusPill)
        root.addView(header)

        // Карточка превью с рамкой и HUD-телеметрией
        previewFrame = FrameLayout(this).apply {
            val pad = (2 * d).toInt()
            setPadding(pad, pad, pad, pad)
        }
        previewView = PreviewView(this).apply {
            onStats = { fps, cols, rows, hex ->
                hudBadge.text = "$fps FPS · ${cols}×${rows} · $hex"
            }
        }
        previewFrame.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        hudBadge = TextView(this).apply {
            text = "60 FPS · 100×44"
            setTextColor(0xFFD8E6DF.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0xB3070A0D.toInt())
                cornerRadius = 8 * d
            }
        }
        previewFrame.addView(
            hudBadge,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(s, s, s, s)
            }
        )

        val hintBadge = TextView(this).apply {
            text = "свайп / тап"
            setTextColor(0x99D8E6DF.toInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding((7 * d).toInt(), (3 * d).toInt(), (7 * d).toInt(), (3 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0x88070A0D.toInt())
                cornerRadius = 8 * d
            }
        }
        previewFrame.addView(
            hintBadge,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(s, s, s, s)
            }
        )

        root.addView(previewFrame, LinearLayout.LayoutParams(MATCH_PARENT, (215 * d).toInt()).apply {
            setMargins(m, 0, m, s)
        })

        // Панель быстрых пресетов
        val presetsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val presets = listOf(
            "Monet" to { applyPreset("fusilli", "monet", "#000000", "classic", 100, true) },
            "Матрица" to { applyPreset("dna", "#6BFF7A", "#050D08", "binary", 110, true) },
            "Янтарь" to { applyPreset("torus", "#FFAA33", "#000000", "dense", 120, true) },
            "Кибер" to { applyPreset("blackhole", "#33E0FF", "#02060F", "classic", 115, true) }
        )
        presets.forEachIndexed { index, (label, action) ->
            val chip = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(0xFFD0DCE5.toInt())
                textSize = 12f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(0xFF121820.toInt())
                    setStroke((1 * d).toInt(), 0xFF232E3B.toInt())
                    cornerRadius = 10 * d
                }
                setOnClickListener { action() }
            }
            presetsRow.addView(chip, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (index < presets.lastIndex) marginEnd = (6 * d).toInt()
            })
        }
        root.addView(presetsRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(m, 0, m, s)
        })

        // Кнопка установки обоев
        applyBtn = TextView(this).apply {
            text = "Установить обои  →"
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, (13 * d).toInt(), 0, (13 * d).toInt())
            setOnClickListener { install() }
        }
        root.addView(applyBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(m, 0, m, s)
        })

        val frame = FrameLayout(this).apply { id = R.id.settings_container }
        root.addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settings_container, Prefs()).commit()
        }
        syncAccentUi()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        previewView.reloadNow()
        syncAccentUi()
        updateWallpaperStatus()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    private fun applyPreset(
        scene: String,
        color: String,
        bg: String,
        chars: String,
        cols: Int,
        trail: Boolean
    ) {
        prefs.edit()
            .putString("scene", scene)
            .putString("color", color)
            .putString("bg", bg)
            .putString("chars", chars)
            .putInt("cols", cols)
            .putBoolean("trail", trail)
            .apply()
        supportFragmentManager.beginTransaction().replace(R.id.settings_container, Prefs()).commit()
        previewView.reloadNow()
        syncAccentUi()
    }

    private fun syncAccentUi() {
        val d = resources.displayMetrics.density
        val cfg = Config.load(this)
        val accent = cfg.color
        val luminance = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255.0

        previewFrame.background = GradientDrawable().apply {
            setColor(cfg.bg)
            setStroke((1.5f * d).toInt(), (accent and 0x00FFFFFF) or 0x66000000)
            cornerRadius = 17 * d
        }

        applyBtn.setTextColor(if (luminance > 0.52) Color.BLACK else Color.WHITE)
        applyBtn.background = GradientDrawable().apply {
            setColor(accent)
            cornerRadius = 12 * d
        }
    }

    private fun updateWallpaperStatus() {
        val d = resources.displayMetrics.density
        val info = try {
            WallpaperManager.getInstance(this).wallpaperInfo
        } catch (_: Exception) {
            null
        }
        val isActive = info?.packageName == packageName
        statusPill.text = if (isActive) "● АКТИВНЫ" else "○ НЕ УСТАНОВЛЕНЫ"
        statusPill.setTextColor(if (isActive) 0xFF6BFF7A.toInt() else 0xFF8C98A4.toInt())
        statusPill.background = GradientDrawable().apply {
            setColor(if (isActive) 0x226BFF7A else 0xFF141A22.toInt())
            setStroke((1 * d).toInt(), if (isActive) 0x556BFF7A else 0xFF25303D.toInt())
            cornerRadius = 99 * d
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
        } catch (_: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    class Prefs : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setDivider(null)
            listView?.clipToPadding = false
            val pad = (8 * resources.displayMetrics.density).toInt()
            listView?.setPadding(0, 0, 0, pad * 2)
        }
    }
}
