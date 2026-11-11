package dev.ascii.live

import android.content.Context
import android.graphics.Color
import android.opengl.*
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class AsciiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = OpenGlEngine()

    private inner class OpenGlEngine : Engine(), Choreographer.FrameCallback {
        private val app = applicationContext
        private val choreo = Choreographer.getInstance()
        private var visible = false
        private var w = 0
        private var h = 0
        private var startTime = System.currentTimeMillis()

        private var eglDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface = EGL14.EGL_NO_SURFACE

        private var program = 0
        private var fontTextureId = 0
        private var uResLoc = -1
        private var uTimeLoc = -1
        private var uColorLoc = -1
        private var uColsLoc = -1
        private var uCharCountLoc = -1
        private var aPosLoc = -1

        private var config = Config.load(app)

        private val quadBuffer: FloatBuffer = ByteBuffer.allocateDirect(32)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }

        private val vertCode = """
            attribute vec2 a_position;
            varying vec2 v_uv;
            void main() {
                v_uv = a_position * 0.5 + 0.5;
                v_uv.y = 1.0 - v_uv.y;
                gl_Position = vec4(a_position, 0.0, 1.0);
            }
        """.trimIndent()

        private val fragCode = """
            precision mediump float;
            varying vec2 v_uv;
            uniform vec2 u_resolution;
            uniform float u_time;
            uniform vec3 u_color;
            uniform sampler2D u_fontTexture;
            uniform float u_cols;
            uniform float u_charCount;

            void main() {
                float aspect = u_resolution.x / u_resolution.y;
                float rows = floor(u_cols / aspect);
                vec2 grid = vec2(u_cols, rows);

                vec2 cell = floor(v_uv * grid);
                vec2 cellUv = fract(v_uv * grid);
                vec2 p = (cell - grid * 0.5) / (grid.y * 0.5);

                float r = length(p);
                float a = atan(p.y, p.x);
                float disk = sin(1.0 / (r + 0.08) * 6.0 - u_time * 3.0 + a * 2.0);
                float lum = smoothstep(0.1, 0.95, disk) * exp(-r * 0.7);
                if (r < 0.22) lum = 0.0;

                float charIdx = floor(clamp(lum, 0.0, 0.99) * u_charCount);
                vec2 fontUv = vec2((cellUv.x + charIdx) / u_charCount, cellUv.y);
                float charAlpha = texture2D(u_fontTexture, fontUv).a;

                gl_FragColor = vec4(u_color * charAlpha * lum, 1.0);
            }
        """.trimIndent()

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            initEgl(holder)
            initGl()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            GLES20.glViewport(0, 0, w, h)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            releaseEgl()
            super.onSurfaceDestroyed(holder)
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
                config = Config.load(app)
                choreo.postFrameCallback(this)
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible || !isInteractive()) return

            renderGl()

            if (visible) {
                choreo.postFrameCallback(this)
            }
        }

        private fun renderGl() {
            if (eglSurface == EGL14.EGL_NO_SURFACE || w <= 0 || h <= 0) return

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            val time = (System.currentTimeMillis() - startTime) / 1000f
            val cInt = config.getEffectiveColor(app)
            val red = Color.red(cInt) / 255f
            val green = Color.green(cInt) / 255f
            val blue = Color.blue(cInt) / 255f

            GLES20.glUniform2f(uResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uTimeLoc, time)
            GLES20.glUniform3f(uColorLoc, red, green, blue)
            GLES20.glUniform1f(uColsLoc, config.cols.toFloat())
            GLES20.glUniform1f(uCharCountLoc, config.ramp.length.toFloat())

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fontTextureId)

            GLES20.glEnableVertexAttribArray(aPosLoc)
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPosLoc)

            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        private fun initEgl(holder: SurfaceHolder) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], holder, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
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
        }

        private fun loadShader(type: Int, code: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, code)
                GLES20.glCompileShader(shader)
            }
        }

        private fun releaseEgl() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
