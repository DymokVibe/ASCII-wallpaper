package dev.ascii.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils

object FontTexture {
    fun create(ramp: String, oldTextureId: Int = 0): Int {
        // Удаляем старую текстуру из VRAM, если она уже существовала
        if (oldTextureId != 0) {
            val textures = intArrayOf(oldTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
        }

        val count = ramp.length
        val charW = 32
        val charH = 32
        val bmp = Bitmap.createBitmap(charW * count, charH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        val baseLine = charH / 2f - (paint.descent() + paint.ascent()) / 2f
        for (i in ramp.indices) {
            canvas.drawText(ramp[i].toString(), i * charW + charW / 2f, baseLine, paint)
        }

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()

        return tex[0]
    }
}
