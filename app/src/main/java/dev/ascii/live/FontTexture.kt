package dev.ascii.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils

object FontTexture {
    fun create(ramp: String): Int {
        val charCount = ramp.length
        val charWidth = 32
        val charHeight = 32
        val bitmap = Bitmap.createBitmap(charWidth * charCount, charHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        val baseLine = charHeight / 2f - (paint.descent() + paint.ascent()) / 2f
        for (i in ramp.indices) {
            canvas.drawText(ramp[i].toString(), i * charWidth + charWidth / 2f, baseLine, paint)
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        return textures[0]
    }
}
