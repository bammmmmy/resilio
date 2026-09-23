package com.example.resilio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class RiskTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val labels = mutableListOf<String>()
    private val values = mutableListOf<Float>()

    private val yLabels = listOf("Critical", "High", "Moderate", "Low")
    private val bandPaints = listOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(52, 213, 104, 129) },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 239, 210, 121) },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(52, 153, 219, 206) },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(52, 180, 218, 190) }
    )
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 95, 118, 142)
        textSize = 22f
        textAlign = Paint.Align.RIGHT
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(217, 143, 16)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(217, 143, 16)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 82, 113, 141)
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    fun setData(items: List<Pair<String, Float>>) {
        labels.clear(); values.clear()
        items.forEach { (day, rainfall) ->
            labels.add(day)
            values.add(rainfall.coerceIn(0f, 90f))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val plotLeft = 84f
        val plotRight = width.toFloat() - 10f
        val plotTop = 10f
        val plotBottom = height.toFloat() - 42f
        val bandHeight = (plotBottom - plotTop) / 4f

        val bandBounds = listOf(
            RectF(plotLeft, plotTop, plotRight, plotTop + bandHeight),
            RectF(plotLeft, plotTop + bandHeight, plotRight, plotTop + 2 * bandHeight),
            RectF(plotLeft, plotTop + 2 * bandHeight, plotRight, plotTop + 3 * bandHeight),
            RectF(plotLeft, plotTop + 3 * bandHeight, plotRight, plotBottom)
        )

        bandBounds.forEachIndexed { index, rect ->
            canvas.drawRect(rect, bandPaints[index])
        }

        yLabels.forEachIndexed { index, label ->
            val y = plotTop + (index * bandHeight) + (bandHeight / 2f)
            canvas.drawText(label, plotLeft - 8f, y + 7f, axisPaint)
        }

        if (values.isEmpty()) return

        val path = Path()
        val step = if (values.size > 1) (plotRight - plotLeft) / (values.size - 1) else 0f
        val maxRain = 80f

        for (index in values.indices) {
            val x = plotLeft + (index * step)
            val normalized = (values[index] / maxRain).coerceIn(0f, 1f)
            val y = plotBottom - (normalized * (plotBottom - plotTop))
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }

            canvas.drawCircle(x, y, 9f, pointPaint)
            canvas.drawCircle(x, y, 9f, pointStrokePaint)
        }

        canvas.drawPath(path, linePaint)

        for (index in values.indices) {
            val x = plotLeft + (index * step)
            val normalized = (values[index] / maxRain).coerceIn(0f, 1f)
            val y = plotBottom - (normalized * (plotBottom - plotTop))
            canvas.drawCircle(x, y, 7f, pointPaint)
            canvas.drawCircle(x, y, 7f, pointStrokePaint)
        }

        if (labels.isNotEmpty()) {
            val labelStep = if (labels.size > 1) (plotRight - plotLeft) / (labels.size - 1) else 0f
            labels.forEachIndexed { index, label ->
                val x = plotLeft + (index * labelStep)
                canvas.drawText(label, x, height.toFloat() - 10f, valuePaint)
            }
        }
    }
}
