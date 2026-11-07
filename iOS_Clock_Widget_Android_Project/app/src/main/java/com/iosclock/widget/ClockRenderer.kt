package com.iosclock.widget

import android.graphics.*
import java.util.Calendar
import kotlin.math.*

object ClockRenderer {

    enum class ThemeMode { LIGHT, DARK, OLED }
    enum class TickStyle { IOS_SECTOR, MINUTE_FILL, SINGLE_DOT }

    /**
     * Находит расстояние вдоль луча (dx, dy) из центра (0,0) до границы
     * скругленного квадрата с полустороной halfS и радиусом скругления radius.
     */
    private fun intersectRoundedRect(dx: Float, dy: Float, halfS: Float, radius: Float): Float {
        val ax = abs(dx)
        val ay = abs(dy)
        val inner = halfS - radius
        val tx = if (ax > 1e-6f) halfS / ax else 1e9f
        val ty = if (ay > 1e-6f) halfS / ay else 1e9f

        if (tx <= ty) {
            if (tx * ay <= inner) return tx
        } else {
            if (ty * ax <= inner) return ty
        }

        val b = -2f * inner * (ax + ay)
        val c = 2f * inner * inner - radius * radius
        val disc = max(0f, b * b - 4f * c)
        return (-b + sqrt(disc)) / 2f
    }

    /**
     * Рисует виджет часов один-в-один как на эталонном фото (скругленный квадрат,
     * 60 радиальных засечек секунд по периметру, узкие высокие цифры и круглые точки двоеточия).
     */
    fun renderClockBitmap(
        sizePx: Int,
        calendar: Calendar = Calendar.getInstance(),
        is24Hour: Boolean = true,
        theme: ThemeMode = ThemeMode.LIGHT,
        tickStyle: TickStyle = TickStyle.IOS_SECTOR,
        blinkColon: Boolean = false
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val pad = sizePx * 0.02f
        val boxW = sizePx - 2f * pad
        val cornerR = boxW * 0.225f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = when (theme) {
                ThemeMode.LIGHT -> Color.rgb(235, 235, 240)
                ThemeMode.DARK -> Color.rgb(28, 28, 30)
                ThemeMode.OLED -> Color.BLACK
            }
        }

        val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)
        canvas.drawRoundRect(rect, cornerR, cornerR, bgPaint)

        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val sOut = boxW * 0.465f
        val rOut = boxW * 0.198f
        val sIn = boxW * 0.428f
        val rIn = boxW * 0.161f

        val sec = calendar.get(Calendar.SECOND)
        val millis = calendar.get(Calendar.MILLISECOND)
        val exactSec = sec + millis / 1000f

        val baseTickRgb = if (theme == ThemeMode.LIGHT) Triple(24, 24, 28) else Triple(242, 242, 247)
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, boxW * 0.0084f)
            strokeCap = Paint.Cap.ROUND
        }

        for (i in 0 until 60) {
            val angleDeg = i * 6f - 90f
            val rad = Math.toRadians(angleDeg.toDouble())
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()

            val tOut = intersectRoundedRect(dx, dy, sOut, rOut)
            val tIn = intersectRoundedRect(dx, dy, sIn, rIn)

            val x1 = cx + dx * tIn
            val y1 = cy + dy * tIn
            val x2 = cx + dx * tOut
            val y2 = cy + dy * tOut

            val alpha = when (tickStyle) {
                TickStyle.IOS_SECTOR -> {
                    // Сектор из 31 темной засечки позади текущей секунды (как на фото 19:45)
                    val diff = (sec - i + 60) % 60
                    when {
                        diff in 0..30 -> 225
                        diff == 31 || diff == 59 -> 140
                        else -> 68
                    }
                }
                TickStyle.MINUTE_FILL -> {
                    if (i <= sec) 225 else 68
                }
                TickStyle.SINGLE_DOT -> {
                    when {
                        i == sec -> 255
                        (sec - i + 60) % 60 == 1 -> 150
                        else -> 75
                    }
                }
            }

            tickPaint.color = Color.argb(alpha, baseTickRgb.first, baseTickRgb.second, baseTickRgb.third)
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // Отрисовка узких цифр (HH и mm) и двух круглых точек двоеточия
        val hourVal = if (is24Hour) {
            calendar.get(Calendar.HOUR_OF_DAY)
        } else {
            val h = calendar.get(Calendar.HOUR)
            if (h == 0) 12 else h
        }
        val minVal = calendar.get(Calendar.MINUTE)

        val hourStr = if (is24Hour) String.format("%02d", hourVal) else hourVal.toString()
        val minStr = String.format("%02d", minVal)

        val textColor = if (theme == ThemeMode.LIGHT) Color.argb(245, 22, 22, 26) else Color.argb(245, 245, 245, 250)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = boxW * 0.44f
            textScaleX = 0.82f // Сжатие по горизонтали для точного совпадения с пропорциями фото
        }

        val boundsH = Rect()
        textPaint.getTextBounds(hourStr, 0, hourStr.length, boundsH)
        val boundsM = Rect()
        textPaint.getTextBounds(minStr, 0, minStr.length, boundsM)

        val colonGap = boxW * 0.075f
        val baselineY = cy + boundsH.height() / 2f - boundsH.bottom

        val hourWidth = textPaint.measureText(hourStr)
        val hourX = cx - colonGap / 2f - hourWidth
        val minX = cx + colonGap / 2f

        canvas.drawText(hourStr, hourX, baselineY, textPaint)
        canvas.drawText(minStr, minX, baselineY, textPaint)

        // Круглые точки двоеточия
        val showColon = !blinkColon || (sec % 2 == 0)
        if (showColon) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = textColor
            }
            val dotRadius = boxW * 0.021f
            val dotOffsetY = boxW * 0.068f
            canvas.drawCircle(cx, cy - dotOffsetY, dotRadius, dotPaint)
            canvas.drawCircle(cx, cy + dotOffsetY, dotRadius, dotPaint)
        }

        return bmp
    }
}
