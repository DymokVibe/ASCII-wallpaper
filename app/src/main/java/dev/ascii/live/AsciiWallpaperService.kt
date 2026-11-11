package dev.ascii.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

class AsciiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AsciiEngine()

    private inner class AsciiEngine : Engine(), Choreographer.FrameCallback, SensorEventListener {
        private val app = applicationContext
        private val session = AsciiSession(app)
        private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        private val choreo = Choreographer.getInstance()
        private var visible = false
        private var w = 0
        private var h = 0
        private var last = 0L
        private var lowPower = false
        private var lastPowerCheck = 0L
        private var tx = 0f
        private var ty = 0f

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            session.reload()
            if (visible) { stopSensors(); startSensors() }
        }
        private val unlockRx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { session.unlock() }
        }

        override fun onCreate(sh: SurfaceHolder) {
            super.onCreate(sh)
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            ContextCompat.registerReceiver(app, unlockRx, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        override fun onDestroy() {
            visible = false
            choreo.removeFrameCallback(this)
            stopSensors()
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            try { app.unregisterReceiver(unlockRx) } catch (_: Exception) {}
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height
            drawFrame(0f)
        }

       private fun isInteractive(): Boolean {
           val pm = app.getSystemService(PowerManager::class.java)
           return pm?.isInteractive ?: true
       }
       
       override fun onVisibilityChanged(v: Boolean) {
           val active = v && isInteractive()
           if (visible == active) return
           visible = active
           choreo.removeFrameCallback(this)
           if (active) {
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
           if (!visible || !isInteractive()) {
               if (visible) onVisibilityChanged(false)
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
       
           if (visible) {
               choreo.postFrameCallback(this)
           }
       }

        private fun drawFrame(dt: Float) {
            var c: Canvas? = null
            try {
                c = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    surfaceHolder.lockHardwareCanvas()
                } else {
                    surfaceHolder.lockCanvas()
                }
                if (c != null) session.frame(c, w, h, dt)
            } catch (_: Exception) {
            } finally {
                if (c != null) try { surfaceHolder.unlockCanvasAndPost(c) } catch (_: Exception) {}
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
                MotionEvent.ACTION_DOWN -> { tx = e.x; ty = e.y }
                MotionEvent.ACTION_MOVE -> { session.drag(e.x - tx, e.y - ty); tx = e.x; ty = e.y }
            }
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
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

        override fun onSensorChanged(e: SensorEvent) { session.tilt(e.values[0], e.values[2]) }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
}
