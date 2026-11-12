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
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var previewView: PreviewView
    private lateinit var previewFrame: FrameLayout
    private lateinit var hudBadge: TextView
    private lateinit var statusPill: TextView
    private lateinit var applyBtn: TextView
    private lateinit var nextSceneBtn: TextView
    private val swatchViews = ArrayList<Pair<String, TextView>>()

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
            setBackgroundColor(0xFF06090D.toInt())
            fitsSystemWindows = true
        }

        // Верхняя панель: заголовок и индикатор статуса
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(m, (12 * d).toInt(), m, s)
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBlock.addView(TextView(this).apply {
            text = "ASCII // LIVE"
            setTextColor(0xFFF2FFF2.toInt())
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.08f
        })
        titleBlock.addView(TextView(this).apply {
            text = "Atlas 2D Engine · Dual-Tone"
            setTextColor(0xFF778594.toInt())
            textSize = 11.5f
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

        // Карточка превью с рамкой, HUD и быстрым переключением сцены
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

        val hintBadge = TextView(this).apply {
            text = "свайп · тап"
            setTextColor(0xB3D8E6DF.toInt())
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0x9906090D.toInt())
                cornerRadius = 8 * d
            }
            setOnClickListener { previewView.triggerChargeDemo() }
        }
        previewFrame.addView(
            hintBadge,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(s, s, s, s)
            }
        )

        nextSceneBtn = TextView(this).apply {
            text = "Сцена ↻"
            setTextColor(0xFFE6F2EC.toInt())
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0xB30D131A.toInt())
                setStroke((1 * d).toInt(), 0x33FFFFFF)
                cornerRadius = 9 * d
            }
            setOnClickListener { cycleNextScene() }
        }
        previewFrame.addView(
            nextSceneBtn,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                setMargins(s, s, s, s)
            }
        )

        hudBadge = TextView(this).apply {
            text = "60 FPS · 100×44"
            setTextColor(0xFFD8E6DF.toInt())
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setPadding((8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0xB306090D.toInt())
                cornerRadius = 8 * d
            }
        }
        previewFrame.addView(
            hudBadge,
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(s, s, s, s)
            }
        )

        root.addView(previewFrame, LinearLayout.LayoutParams(MATCH_PARENT, (210 * d).toInt()).apply {
            setMargins(m, 0, m, s)
        })

        // Полоса быстрых цветовых свотчей
        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val swatches = listOf(
            "monet" to "M",
            "#F2FFF2" to "",
            "#6BFF7A" to "",
            "#FFAA33" to "",
            "#33E0FF" to "",
            "#FF5FA2" to "",
            "#B388FF" to ""
        )
        swatchViews.clear()
        swatches.forEachIndexed { idx, (rawColor, badge) ->
            val holder = FrameLayout(this)
            val circle = TextView(this).apply {
                text = badge
                gravity = Gravity.CENTER
                textSize = 12f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setOnClickListener {
                    prefs.edit().putString("color", rawColor).apply()
                    syncFragment()
                    previewView.reloadNow()
                    syncAccentUi()
                }
            }
            val sizePx = (34 * d).toInt()
            holder.addView(circle, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
            swatchViews.add(rawColor to circle)
            swatchRow.addView(holder, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                if (idx < swatches.lastIndex) marginEnd = (4 * d).toInt()
            })
        }
        root.addView(swatchRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(m, 0, m, s)
        })

        // Панель готовых пресетов
        val presetsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val presets = listOf(
            "Monet" to { applyPreset("fusilli", "monet", "#000000", "classic", 100, true, true) },
            "Матрица" to { applyPreset("dna", "#6BFF7A", "#050D08", "binary", 110, false, true) },
            "Сфера" to { applyPreset("sphere", "#33E0FF", "#02060F", "classic", 115, true, true) },
            "Янтарь" to { applyPreset("torus", "#FFAA33", "#000000", "dense", 120, true, true) }
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
                    setColor(0xFF10161E.toInt())
                    setStroke((1 * d).toInt(), 0xFF212C39.toInt())
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

    private fun cycleNextScene() {
        val current = prefs.getString("scene", "fusilli") ?: "fusilli"
        val all = Scenes.names
        val idx = all.indexOf(current)
        val next = all[(idx + 1) % all.size]
        prefs.edit().putString("scene", next).apply()
        syncFragment()
        previewView.reloadNow()
    }

    private fun applyPreset(
        scene: String,
        color: String,
        bg: String,
        chars: String,
        cols: Int,
        duotone: Boolean,
        trail: Boolean
    ) {
        prefs.edit()
            .putString("scene", scene)
            .putString("color", color)
            .putString("bg", bg)
            .putString("chars", chars)
            .putInt("cols", cols)
            .putBoolean("duotone", duotone)
            .putBoolean("trail", trail)
            .apply()
        syncFragment()
        previewView.reloadNow()
        syncAccentUi()
    }

    private fun syncFragment() {
        (supportFragmentManager.findFragmentById(R.id.settings_container) as? Prefs)?.syncFromPrefs()
    }

    private fun syncAccentUi() {
        val d = resources.displayMetrics.density
        val cfg = Config.load(this)
        val accent = cfg.color
        val selectedRaw = prefs.getString("color", "monet") ?: "monet"
        val luminance = (0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent)) / 255.0

        previewFrame.background = GradientDrawable().apply {
            setColor(cfg.bg)
            setStroke((1.5f * d).toInt(), (accent and 0x00FFFFFF) or 0x77000000)
            cornerRadius = 19 * d
        }

        swatchViews.forEach { (raw, view) ->
            val resolved = Config.resolveAccent(this, raw)
            val isSelected = raw == selectedRaw
            val lum = (0.299 * Color.red(resolved) + 0.587 * Color.green(resolved) + 0.114 * Color.blue(resolved)) / 255.0
            view.setTextColor(if (lum > 0.52) Color.BLACK else Color.WHITE)
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(resolved)
                if (isSelected) {
                    setStroke((2.5f * d).toInt(), Color.WHITE)
                } else {
                    setStroke((1 * d).toInt(), 0x33FFFFFF)
                }
            }
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
        statusPill.text = if (isActive) "● АКТИВНЫ" else "○ ОФФЛАЙН"
        statusPill.setTextColor(if (isActive) 0xFF6BFF7A.toInt() else 0xFF8C98A4.toInt())
        statusPill.background = GradientDrawable().apply {
            setColor(if (isActive) 0x226BFF7A else 0xFF121820.toInt())
            setStroke((1 * d).toInt(), if (isActive) 0x556BFF7A else 0xFF232E3B.toInt())
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
            val charsPref = findPreference<ListPreference>("chars")
            val customPref = findPreference<EditTextPreference>("custom_chars")
            customPref?.isVisible = (charsPref?.value == "custom")
            charsPref?.setOnPreferenceChangeListener { _, newValue ->
                customPref?.isVisible = (newValue == "custom")
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setDivider(null)
            listView?.clipToPadding = false
            val pad = (8 * resources.displayMetrics.density).toInt()
            listView?.setPadding(0, 0, 0, pad * 2)
        }

        fun syncFromPrefs() {
            val p = preferenceManager.sharedPreferences ?: return
            findPreference<ListPreference>("scene")?.value = p.getString("scene", "fusilli")
            findPreference<ListPreference>("color")?.value = p.getString("color", "monet")
            findPreference<ListPreference>("bg")?.value = p.getString("bg", "#000000")
            val charsVal = p.getString("chars", "classic") ?: "classic"
            findPreference<ListPreference>("chars")?.value = charsVal
            findPreference<EditTextPreference>("custom_chars")?.apply {
                text = p.getString("custom_chars", " .:-=+*#%@")
                isVisible = charsVal == "custom"
            }
            findPreference<SeekBarPreference>("cols")?.value = p.getInt("cols", 100)
            findPreference<SeekBarPreference>("thick")?.value = p.getInt("thick", 1)
            findPreference<SwitchPreferenceCompat>("duotone")?.isChecked = p.getBoolean("duotone", true)
            findPreference<SwitchPreferenceCompat>("trail")?.isChecked = p.getBoolean("trail", true)
            findPreference<SeekBarPreference>("speed")?.value = p.getInt("speed", 15)
            findPreference<ListPreference>("fps")?.value = p.getString("fps", "60")
            findPreference<SwitchPreferenceCompat>("swipe")?.isChecked = p.getBoolean("swipe", true)
            findPreference<SwitchPreferenceCompat>("tilt")?.isChecked = p.getBoolean("tilt", false)
            findPreference<SwitchPreferenceCompat>("touch")?.isChecked = p.getBoolean("touch", true)
            findPreference<SwitchPreferenceCompat>("unlock")?.isChecked = p.getBoolean("unlock", true)
            findPreference<SwitchPreferenceCompat>("saver")?.isChecked = p.getBoolean("saver", true)
        }
    }
}
