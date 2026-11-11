package dev.ascii.live

import android.content.SharedPreferences
import android.graphics.Color
import android.opengl.*
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.preference.PreferenceManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class AsciiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = OpenGlEngine()

    private inner class OpenGlEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        private var config = Config.load(applicationContext)
        
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var program = 0
        private var fontTextureId = 0
        private var uResLoc = -1
        private var uTimeLoc = -1
        private var uColorLoc = -1
        private var uColsLoc = -1
        private var uCharCountLoc = -1
        private var uSceneLoc = -1
        private var aPosLoc = -1

        private var w = 0
        private var h = 0
        private val startTime = System.currentTimeMillis()

        private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(32)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }

        private val vertCode = "attribute vec2 a_position; varying vec2 v_uv; void main() { v_uv = a_position * 0.5 + 0.5; v_uv.y = 1.0 - v_uv.y; gl_Position = vec4(a_position, 0.0, 1.0); }"

        private val fragCode = """
            precision mediump float;
            varying vec2 v_uv;
            uniform vec2 u_resolution;
            uniform float u_time;
            uniform vec3 u_color;
            uniform sampler2D u_fontTexture;
            uniform float u_cols;
            uniform float u_charCount;
            uniform int u_scene;

            mat2 rot(float a) { float s = sin(a), c = cos(a); return mat2(c, -s, s, c); }
            float hash(float n) { return fract(sin(n) * 43758.5453123); }
            float sdBox(vec3 p, vec3 b) { vec3 q = abs(p) - b; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0); }

            float mapScene(vec3 p, float time, int scene) {
                if (scene == 1) {
                    vec3 q = p; q.xz *= rot(time * 0.5); q.xy *= rot(time * 0.35);
                    return sdBox(mod(q + 1.2, 2.4) - 1.2, vec3(0.38));
                } else if (scene == 2) {
                    vec3 q = p; q.xz *= rot(time * 0.6); q.xy *= rot(time * 0.4);
                    return sdBox(q, vec3(0.75));
                } else {
                    vec3 q = p; q.yz *= rot(0.25); q.xz *= rot(q.y * 1.8 + time * 2.0);
                    return length(q.xz - vec2(0.42, 0.0)) - 0.14;
                }
            }

            vec3 calcNormal(vec3 p, float time, int scene) {
                vec2 e = vec2(0.005, 0.0);
                return normalize(vec3(
                    mapScene(p + e.xyy, time, scene) - mapScene(p - e.xyy, time, scene),
                    mapScene(p + e.yxy, time, scene) - mapScene(p - e.yxy, time, scene),
                    mapScene(p + e.yyx, time, scene) - mapScene(p - e.yyx, time, scene)
                ));
            }

            float renderRaymarch(vec2 p, float time, int scene) {
                vec3 ro = vec3(0.0, 0.0, -4.2);
                vec3 rd = normalize(vec3(p, 1.8));
                float t = 0.0;
                for (int i = 0; i < 24; i++) {
                    vec3 pos = ro + rd * t;
                    float d = mapScene(pos, time, scene);
                    if (d < 0.01) {
                        vec3 n = calcNormal(pos, time, scene);
                        float diff = max(dot(n, normalize(vec3(0.5, 0.8, -1.0))), 0.15);
                        return clamp(diff * exp(-t * 0.22), 0.0, 1.0);
                    }
                    if (t > 7.0) break;
                    t += d * 0.9;
                }
                return 0.0;
            }

            void main() {
                float aspect = u_resolution.x / u_resolution.y;
                vec2 grid = vec2(u_cols, floor(u_cols / aspect));
                vec2 cell = floor(v_uv * grid);
                vec2 cellUv = clamp(fract(v_uv * grid), 0.02, 0.98);
                vec2 p = (cell - grid * 0.5) / (grid.y * 0.5);

                float lum = (u_scene == 5) ? fract(cell.y / grid.y + u_time * 0.5) : renderRaymarch(p, u_time, u_scene);
                float charIdx = floor(clamp(lum > 0.02 ? lum : 0.0, 0.0, 0.99) * u_charCount);

                vec2 fontUv = vec2((cellUv.x + charIdx) / u_charCount, cellUv.y);
                float charAlpha = texture2D(u_fontTexture, fontUv).a;
                gl_FragColor = vec4(u_color * charAlpha * lum, 1.0);
            }
        """.trimIndent()

        init { prefs.registerOnSharedPreferenceChangeListener(this) }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            config = Config.load(applicationContext)
            if (fontTextureId != 0) fontTextureId = FontTexture.create(config.ramp, fontTextureId)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            initEgl(holder)
            initGl()
            drawFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height
            GLES20.glViewport(0, 0, w, h)
            drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) drawFrame()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releaseEgl()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            releaseEgl()
            super.onDestroy()
        }

        private fun initEgl(holder: SurfaceHolder) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
            val configAttribs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], holder, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun releaseEgl() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY; eglContext = EGL14.EGL_NO_CONTEXT; eglSurface = EGL14.EGL_NO_SURFACE
        }

        private fun initGl() {
            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertCode)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragCode)
            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vShader)
                GLES20.glAttachShader(it, fShader)
                GLES20.glLinkProgram(it)
            }
            fontTextureId = FontTexture.create(config.ramp)
            aPosLoc = GLES20.glGetAttribLocation(program, "a_position")
            uResLoc = GLES20.glGetUniformLocation(program, "u_resolution")
            uTimeLoc = GLES20.glGetUniformLocation(program, "u_time")
            uColorLoc = GLES20.glGetUniformLocation(program, "u_color")
            uColsLoc = GLES20.glGetUniformLocation(program, "u_cols")
            uCharCountLoc = GLES20.glGetUniformLocation(program, "u_charCount")
            uSceneLoc = GLES20.glGetUniformLocation(program, "u_scene")
        }

        private fun drawFrame() {
            if (w <= 0 || h <= 0 || eglSurface == EGL14.EGL_NO_SURFACE) return
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            val time = (System.currentTimeMillis() - startTime) / 1000f
            val cInt = config.getEffectiveColor(applicationContext)

            GLES20.glUniform2f(uResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uTimeLoc, time)
            GLES20.glUniform3f(uColorLoc, Color.red(cInt) / 255f, Color.green(cInt) / 255f, Color.blue(cInt) / 255f)
            GLES20.glUniform1f(uColsLoc, config.cols.toFloat())
            GLES20.glUniform1f(uCharCountLoc, config.ramp.length.toFloat())
            GLES20.glUniform1i(uSceneLoc, when(config.scene) { "cubes" -> 1; "bigcube" -> 2; "matrix" -> 5; else -> 0 })

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fontTextureId)
            GLES20.glEnableVertexAttribArray(aPosLoc)
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPosLoc)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        private fun loadShader(type: Int, code: String): Int {
            return GLES20.glCreateShader(type).also {
                GLES20.glShaderSource(it, code)
                GLES20.glCompileShader(it)
            }
        }
    }
}
