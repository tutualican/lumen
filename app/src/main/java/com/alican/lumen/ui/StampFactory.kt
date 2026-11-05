package com.alican.lumen.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders the retro "date back" stamp that some presets overlay. */
object StampFactory {

    fun create(date: Date = Date(), tint: Int = Color.rgb(255, 128, 40)): Bitmap {
        val width = 512
        val height = 160
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            textSize = 104f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.08f
            setShadowLayer(10f, 0f, 0f, (tint and 0x00FFFFFF) or 0x66000000)
        }
        val text = SimpleDateFormat("'yy  MM  dd", Locale.US).format(date)
        val x = (width - paint.measureText(text)) / 2f
        val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)
        return bitmap
    }
}
