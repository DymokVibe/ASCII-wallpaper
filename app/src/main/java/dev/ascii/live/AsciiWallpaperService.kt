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
        private var isEngineVisible = false
        private var isRunning = false
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
        private var uSceneLoc = -1
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
            uniform int u_scene;

            float sceneMatrix(vec2 cell, vec2 grid, float time) {
                float col = cell.x;
                float speed = fract(sin(col * 12.9898) * 43758.5453) * 1.5 + 0.8;
                float drop = fract(cell.y / grid.y + time * speed * 0.2 + sin(col));
                return smoothstep(0.0, 1.0, drop) * step(0.05, drop);
            }

            float sceneBlackHole(vec2 p, float time) {
                float r = length(p);
                float a = atan(p.y, p.x);
                float disk = sin(1.0 / (r + 0.08) * 6.0 - time * 3.0 + a * 2.0);
                float lum = smoothstep(0.1, 0.95, disk) * exp(-r * 0.7);
                if (r < 0.22) lum = 0.0;
                return lum;
            }

            float sceneFusilli(vec2 p, float time) {
                float a = atan(p.y, p.x);
                float r = length(p);
                float wave = sin(r * 10.0 - time * 4.0 + a * 3.0);
                return smoothstep(0.2, 0.85, wave) * exp(-r * 0.5);
            }

            float sceneDNA(vec2 p, float time) {
                float y = p.y * 4.0;
                float s1 = sin(y + time * 2.5) * 0.45;
                float s2 = sin(y + time * 2.5 + 3.14159) * 0.45;
                float d1 = exp(-pow(p.x - s1, 2.0) * 35.0);
                float d2 = exp(-pow(p.x - s2, 2.0) * 35.0);
                return max(d1, d2);
            }

            float sceneCubes(vec2 p, float time) {
                vec2 q = abs(fract(p * 2.5 + vec2(sin(time * 0.8), cos(time * 0.8))) - 0.5);
                return step(max(q.x, q.y), 0.38) * 0.8;
            }

            void main() {
                float aspect = u_resolution.x / u_resolution.y;
                float rows = floor(u_cols / aspect);
                vec2 grid = vec2(u_cols, rows);

                vec2 cell = floor(v_uv * grid);
                vec2 cellUv = fract(v_uv * grid);
                vec2 p = (cell - grid * 0.5) / (grid.y * 0.5);

                float lum = 0.0;
                if (u_scene == 5) {
                    lum = sceneMatrix(cell, grid, u_time);
                } else if (u_scene == 4) {
                    lum = sceneBlackHole(p, u_time);
                } else if (u_scene == 3) {
                    lum = sceneDNA(p, u_time);
                } else if (u_scene == 2 || u_scene == 1) {
                    lum = sceneCubes(p, u_time);
                } else {
                    lum = sceneFusilli(p, u_time);
                }

                float charIdx = floor(clamp(lum, 0.0, 0.99) * u_charCount);
                vec2 fontUv = vec2((cellUv.x + charIdx) / u_charCount, cellUv.y);
                float charAlpha = texture2D(u_fontTexture, fontUv).a;

                gl_FragColor = vec4(u_color * charAlpha * lum, 1.0);
            }
        """.trimIndent()

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width > 0 && height > 0) {
                w = width
                h = height
                if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                    initEgl(holder)
                    initGl()
                } else {
                    GLES20.glViewport(0, 0, w, h)
                }
                startLoopIfNeeded()
            }
        }

        override fun onVisibilityChanged(v: Boolean) {
            super.onVisibilityChanged(v)
            isEngineVisible = v
            if (v) {
                config = Config.load(app)
                startLoopIfNeeded()
            } else {
                stopLoop()
            }
        }

        private fun startLoopIfNeeded() {
            if (isEngineVisible && w > 0 && h > 0 && !isRunning && eglDisplay != EGL14.EGL_NO_DISPLAY) {
                isRunning = true
                choreo.removeFrameCallback(this)
                choreo.postFrameCallback(this)
            }
        }

        private fun stopLoop() {
            isRunning = false
            choreo.removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning || !isEngineVisible) return

            renderGl()

            if (isRunning && isEngineVisible) {
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

            val sceneId = when (config.scene) {
                "cubes" -> 1
                "bigcube" -> 2
                "dna" -> 3
                "blackhole" -> 4
                "matrix" -> 5
                else -> 0
            }

            GLES20.glUniform2f(uResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uTimeLoc, time)
            GLES20.glUniform3f(uColorLoc, red, green, blue)
            GLES20.glUniform1f(uColsLoc, config.cols.toFloat())
            GLES20.glUniform1f(uCharCountLoc, config.ramp.length.toFloat())
            GLES20.glUniform1i(uSceneLoc, sceneId)

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
            uSceneLoc = GLES20.glGetUniformLocation(program, "u_scene")
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

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopLoop()
            releaseEgl()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            stopLoop()
            releaseEgl()
            super.onDestroy()
        }
    }
}
