package com.darkhorses.PedalConnect.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

data class GeoPoint(val lat: Double, val lon: Double)

fun renderRouteToBitmap(points: List<GeoPoint>): Bitmap? {
    if (points.size < 2) return null

    val width = 800
    val height = 400
    val padding = 40f

    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLon = points.minOf { it.lon }
    val maxLon = points.maxOf { it.lon }

    val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 0.001
    val lonRange = (maxLon - minLon).takeIf { it > 0 } ?: 0.001

    fun toX(lon: Double) = padding + ((lon - minLon) / lonRange * (width - 2 * padding)).toFloat()
    fun toY(lat: Double) = (height - padding) - ((lat - minLat) / latRange * (height - 2 * padding)).toFloat()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.parseColor("#F0FAF5"))

    // Draw route line
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D7050")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    points.forEachIndexed { i, pt ->
        if (i == 0) path.moveTo(toX(pt.lon), toY(pt.lat))
        else path.lineTo(toX(pt.lon), toY(pt.lat))
    }
    canvas.drawPath(path, linePaint)

    // Start marker (green dot)
    val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#06402B")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(toX(points.first().lon), toY(points.first().lat), 12f, startPaint)
    canvas.drawCircle(toX(points.first().lon), toY(points.first().lat), 6f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL })

    // End marker (red dot)
    val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DC2626")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(toX(points.last().lon), toY(points.last().lat), 12f, endPaint)
    canvas.drawCircle(toX(points.last().lon), toY(points.last().lat), 6f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL })

    return bitmap
}