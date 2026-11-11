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

            mat2 rot(float a) {
                float s = sin(a), c = cos(a);
                return mat2(c, -s, s, c);
            }

            float hash(float n) { return fract(sin(n) * 43758.5453123); }

            float sdBox(vec3 p, vec3 b) {
                vec3 q = abs(p) - b;
                return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
            }

            float mapScene(vec3 p, float time, int scene) {
                if (scene == 1) { // Cubes Grid
                    vec3 q = p;
                    q.xz *= rot(time * 0.6);
                    q.xy *= rot(time * 0.4);
                    q = mod(q + 1.2, 2.4) - 1.2;
                    return sdBox(q, vec3(0.45));
                } else if (scene == 2) { // Big Cube
                    vec3 q = p;
                    q.xz *= rot(time * 0.7);
                    q.xy *= rot(time * 0.5);
                    return sdBox(q, vec3(0.85));
                } else if (scene == 3) { // 3D DNA Helix
                    vec3 q = p;
                    q.xz *= rot(q.y * 1.8 + time * 2.0);
                    float d1 = length(q.xz - vec2(0.55, 0.0)) - 0.16;
                    float d2 = length(q.xz + vec2(0.55, 0.0)) - 0.16;
                    float rbar = length(vec2(q.x, mod(q.y + 0.1, 0.4) - 0.2)) - 0.05;
                    float bars = max(rbar, abs(q.z) - 0.55);
                    return min(min(d1, d2), bars);
                } else if (scene == 4) { // 3D Black Hole Accretion Disk
                    float r = length(p.xz);
                    float disk = length(vec2(r - 1.2, p.y)) - 0.08;
                    float hole = length(p) - 0.55;
                    return max(-hole, disk);
                } else { // Fusilli Spiral
                    vec3 q = p;
                    q.xz *= rot(q.y * 2.2 + time * 2.5);
                    return length(q.xz - vec2(0.5, 0.0)) - 0.18;
                }
            }

            vec3 calcNormal(vec3 p, float time, int scene) {
                vec2 e = vec2(0.003, 0.0);
                return normalize(vec3(
                    mapScene(p + e.xyy, time, scene) - mapScene(p - e.xyy, time, scene),
                    mapScene(p + e.yxy, time, scene) - mapScene(p - e.yxy, time, scene),
                    mapScene(p + e.yyx, time, scene) - mapScene(p - e.yyx, time, scene)
                ));
            }

            float renderRaymarch(vec2 p, float time, int scene) {
                vec3 ro = vec3(0.0, 0.0, -3.2);
                vec3 rd = normalize(vec3(p, 1.8));

                float t = 0.0;
                for (int i = 0; i < 48; i++) {
                    vec3 pos = ro + rd * t;
                    float d = mapScene(pos, time, scene);
                    if (d < 0.005) {
                        vec3 n = calcNormal(pos, time, scene);
                        vec3 lightDir = normalize(vec3(0.5, 0.8, -1.0));
                        float diff = max(dot(n, lightDir), 0.15);
                        float spec = pow(max(dot(reflect(-lightDir, n), -rd), 0.0), 16.0);
                        float occ = exp(-t * 0.25);
                        return clamp((diff + spec * 0.6) * occ, 0.0, 1.0);
                    }
                    if (t > 7.0) break;
                    t += d * 0.75;
                }
                return 0.0;
            }

            float sceneMatrix(vec2 cell, vec2 grid, float time) {
                float col = cell.x;
                float rng = hash(col * 127.1);
                float speed = 0.4 + rng * 0.8;
                float drop = fract(cell.y / grid.y + time * speed * 0.25 + rng);
                float head = smoothstep(0.88, 1.0, drop);
                float tail = smoothstep(0.0, 0.88, drop) * drop;
                return tail + head * 1.8;
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
                } else {
                    lum = renderRaymarch(p, u_time, u_scene);
                }

                float charRng = hash(cell.x * 31.0 + cell.y * 17.0 + floor(u_time * 8.0));
                float charIdx = floor(clamp(lum > 0.02 ? (u_scene == 5 ? charRng : lum) : 0.0, 0.0, 0.99) * u_charCount);

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
