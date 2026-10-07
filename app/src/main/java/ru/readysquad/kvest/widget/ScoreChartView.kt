package ru.readysquad.kvest.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import ru.readysquad.kvest.data.ProgressPoint

/**
 * График прогресса: как менялись очки от квеста к квесту.
 * Линия «прорисовывается» анимацией при открытии экрана.
 */
class ScoreChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var points: List<ProgressPoint> = emptyList()
    private var fraction = 0f

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#7C5CFF")
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC93C") }
    private val dotCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#141A2A") }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FFFFFF")
        strokeWidth = 2f
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A93A8")
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            fraction = it.animatedValue as Float
            invalidate()
        }
    }

    fun setData(data: List<ProgressPoint>) {
        points = data
        fraction = 0f
        animator.cancel()
        animator.start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padLeft = 44f
        val padRight = 20f
        val padTop = 26f
        val padBottom = 46f
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        // сетка
        for (i in 0..3) {
            val y = padTop + chartH * i / 3f
            canvas.drawLine(padLeft, y, w - padRight, y, grid)
        }

        if (points.isEmpty()) {
            label.textSize = 34f
            canvas.drawText("Пока нет попыток — пройди первый квест", w / 2f, h / 2f, label)
            return
        }

        val maxScore = maxOf(points.maxOf { it.score }, 10)
        val n = points.size
        val stepX = if (n > 1) chartW / (n - 1) else 0f

        val coords = ArrayList<Pair<Float, Float>>(n)
        points.forEachIndexed { i, p ->
            val x = padLeft + stepX * i
            val y = padTop + chartH - (chartH * (p.score.toFloat() / maxScore))
            coords.add(x to y)
        }

        // видимая часть линии по мере анимации
        val visible = (coords.size * fraction).toInt().coerceAtLeast(1)

        path.reset()
        val shown = coords.take(visible.coerceAtMost(coords.size))
        shown.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (shown.size >= 2) {
            // заливка под линией
            val area = Path(path)
            area.lineTo(shown.last().first, padTop + chartH)
            area.lineTo(shown.first().first, padTop + chartH)
            area.close()
            fill.shader = LinearGradient(
                0f, padTop, 0f, padTop + chartH,
                Color.parseColor("#667C5CFF"), Color.parseColor("#007C5CFF"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(area, fill)
        }
        canvas.drawPath(path, line)

        shown.forEach { (x, y) ->
            canvas.drawCircle(x, y, 11f, dot)
            canvas.drawCircle(x, y, 5f, dotCore)
        }

        // подписи по краям
        label.textSize = 28f
        canvas.drawText(points.first().label, padLeft, h - 14f, label)
        if (n > 1) canvas.drawText(points.last().label, w - padRight, h - 14f, label)
    }
}
