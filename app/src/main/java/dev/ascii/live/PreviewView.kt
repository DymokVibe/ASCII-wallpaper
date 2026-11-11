package dev.ascii.live

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

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
    private var config = Config.load(context)

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
            if (scene == 1) {
                vec3 q = p;
                q.xz *= rot(time * 0.5);
                q.xy *= rot(time * 0.35);
                q = mod(q + 1.2, 2.4) - 1.2;
                return sdBox(q, vec3(0.38));
            } else if (scene == 2) {
                vec3 q = p;
                q.xz *= rot(time * 0.6);
                q.xy *= rot(time * 0.4);
                return sdBox(q, vec3(0.75));
            } else if (scene == 3) {
                vec3 q = p;
                q.yz *= rot(0.2);
                q.xz *= rot(q.y * 1.5 + time * 1.8);
                float d1 = length(q.xz - vec2(0.4, 0.0)) - 0.12;
                float d2 = length(q.xz + vec2(0.4, 0.0)) - 0.12;
                float rbar = length(vec2(q.x, mod(q.y + 0.1, 0.35) - 0.175)) - 0.04;
                float bars = max(rbar, abs(q.z) - 0.4);
                return min(min(d1, d2), bars);
            } else if (scene == 4) {
                vec3 q = p;
                q.yz *= rot(0.75);
                q.xz *= rot(time * 0.8);
                float r = length(q.xz);
                float disk = length(vec2(r - 1.05, q.y)) - 0.06;
                float hole = length(q) - 0.48;
                return max(-hole, disk);
            } else {
                vec3 q = p;
                q.yz *= rot(0.25);
                q.xz *= rot(q.y * 1.8 + time * 2.0);
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
            for (int i = 0; i < 32; i++) {
                vec3 pos = ro + rd * t;
                float d = mapScene(pos, time, scene);
                if (d < 0.008) {
                    vec3 n = calcNormal(pos, time, scene);
                    vec3 lightDir = normalize(vec3(0.5, 0.8, -1.0));
                    float diff = max(dot(n, lightDir), 0.15);
                    float spec = pow(max(dot(reflect(-lightDir, n), -rd), 0.0), 16.0);
                    float occ = exp(-t * 0.22);
                    return clamp((diff + spec * 0.6) * occ, 0.0, 1.0);
                }
                if (t > 7.5) break;
                t += d * 0.85;
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
            vec2 safeCellUv = clamp(cellUv, 0.02, 0.98);
            vec2 p = (cell - grid * 0.5) / (grid.y * 0.5);

            float lum = 0.0;
            if (u_scene == 5) {
                lum = sceneMatrix(cell, grid, u_time);
            } else {
                lum = renderRaymarch(p, u_time, u_scene);
            }

            float charRng = hash(cell.x * 31.0 + cell.y * 17.0 + floor(u_time * 8.0));
            float charIdx = floor(clamp(lum > 0.02 ? (u_scene == 5 ? charRng : lum) : 0.0, 0.0, 0.99) * u_charCount);

            vec2 fontUv = vec2((safeCellUv.x + charIdx) / u_charCount, safeCellUv.y);
            float charAlpha = texture2D(u_fontTexture, fontUv).a;

            gl_FragColor = vec4(u_color * charAlpha * lum, 1.0);
        }
    """.trimIndent()

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun reloadConfig() {
        queueEvent {
            config = Config.load(context)
            if (fontTextureId != 0) {
                fontTextureId = FontTexture.create(config.ramp, fontTextureId)
            }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, eglConfig: EGLConfig?) {
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

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width
        h = height
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (w <= 0 || h <= 0) return

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val time = (System.currentTimeMillis() - startTime) / 1000f
        val cInt = config.getEffectiveColor(context)

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
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }
}
