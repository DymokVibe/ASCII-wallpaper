package dev.ascii.live

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

class AsciiWallpaperService : WallpaperService() {
    private var activeEngine: AsciiEngine? = null

    override fun onCreateEngine(): Engine = AsciiEngine().also { activeEngine = it }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        activeEngine?.onSystemThemeChanged()
    }

    private inner class AsciiEngine : Engine(), Choreographer.FrameCallback, SensorEventListener {
        private val app = applicationContext
        private val session = AsciiSession(app)
        private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        private val choreo = Choreographer.getInstance()
        private var running = false
        private var w = 0
        private var h = 0
        private var last = 0L
        private var lowPower = false
        private var tx = 0f
        private var ty = 0f

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            session.reload()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    notifyColorsChanged()
                } catch (_: Exception) {}
            }
            if (running) {
                stopSensors()
                startSensors()
            } else {
                drawFrame(0f)
            }
        }

        private val screenRx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> setRunning(false)
                    Intent.ACTION_USER_PRESENT -> {
                        session.unlock()
                        if (isVisible) setRunning(true)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (isVisible) setRunning(true)
                    }
                }
            }
        }

        fun onSystemThemeChanged() {
            session.reload()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    notifyColorsChanged()
                } catch (_: Exception) {}
            }
            drawFrame(0f)
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val primary = Color.valueOf(session.cfg.color)
                val bg = Color.valueOf(session.cfg.bg)
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    WallpaperColors(primary, bg, primary, WallpaperColors.HINT_SUPPORTS_DARK_THEME)
                } else {
                    WallpaperColors(primary, bg, primary)
                }
            }
            return super.onComputeColors()
        }

        override fun onCreate(sh: SurfaceHolder) {
            super.onCreate(sh)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(app, screenRx, filter, ContextCompat.RECEIVER_EXPORTED)
        }

        override fun onDestroy() {
            if (activeEngine === this) activeEngine = null
            setRunning(false)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            try {
                app.unregisterReceiver(screenRx)
            } catch (_: Exception) {}
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            drawFrame(0f)
            if (isVisible) setRunning(true)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame(0f)
            if (isVisible) setRunning(true)
        }

        override fun onVisibilityChanged(v: Boolean) {
            super.onVisibilityChanged(v)
            setRunning(v)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            session.pageOffset(xOffset)
        }

        private fun setRunning(shouldRun: Boolean) {
            if (running == shouldRun) {
                if (shouldRun) {
                    choreo.removeFrameCallback(this)
                    choreo.postFrameCallback(this)
                }
                return
            }
            running = shouldRun
            choreo.removeFrameCallback(this)
            if (shouldRun) {
                session.reload()
                checkPower()
                last = 0L
                startSensors()
                choreo.postFrameCallback(this)
            } else {
                stopSensors()
            }
        }

        override fun doFrame(ns: Long) {
            if (!running || !isVisible) {
                if (running) setRunning(false)
                return
            }
            if (last == 0L) last = ns
            val targetFps = session.effectiveFps(lowPower)
            val interval = 1_000_000_000L / targetFps
            val elapsed = ns - last

            if (elapsed >= interval - 2_000_000L) {
                val dt = (elapsed / 1e9f).coerceAtMost(0.1f)
                last = ns
                drawFrame(dt)
            }

            if (running && isVisible) {
                choreo.postFrameCallback(this)
            }
        }

        private fun drawFrame(dt: Float) {
            if (w <= 0 || h <= 0) return
            var c: Canvas? = null
            try {
                c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    surfaceHolder.lockHardwareCanvas()
                } else {
                    surfaceHolder.lockCanvas()
                }
                if (c != null) session.frame(c, w, h, dt)
            } catch (_: Exception) {
            } finally {
                if (c != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(c)
                    } catch (_: Exception) {}
                }
            }
        }

        private fun checkPower() {
            val pm = app.getSystemService(PowerManager::class.java)
            val bm = app.getSystemService(BatteryManager::class.java)
            val low = bm != null &&
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) in 1..15 && !bm.isCharging
            lowPower = (pm != null && pm.isPowerSaveMode) || low
        }

        override fun onTouchEvent(e: MotionEvent) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tx = e.x
                    ty = e.y
                }
                MotionEvent.ACTION_MOVE -> {
                    session.drag(e.x - tx, e.y - ty)
                    tx = e.x
                    ty = e.y
                }
            }
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: Bundle?,
            resultRequested: Boolean
        ): Bundle? {
            if (action == "android.wallpaper.tap") session.kick()
            return null
        }

        private fun startSensors() {
            if (!session.cfg.tilt) return
            val sm = app.getSystemService(SensorManager::class.java) ?: return
            val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        }

        private fun stopSensors() {
            app.getSystemService(SensorManager::class.java)?.unregisterListener(this)
        }

        override fun onSensorChanged(e: SensorEvent) {
            session.tilt(e.values[0], e.values[2])
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
}
