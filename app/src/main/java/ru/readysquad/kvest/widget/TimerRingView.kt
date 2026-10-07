package ru.readysquad.kvest.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Кольцо таймера задачи. Показывает, сколько секунд осталось на ответ,
 * и меняет цвет, когда время поджимает (на этом основан бонус за скорость).
 */
class TimerRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.parseColor("#33FFFFFF")
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00E0B8")
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rect = RectF()

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var secondsLeft: Int = 0
        set(value) {
            field = value
            ring.color = when {
                value <= 3 -> Color.parseColor("#FF5A6E")
                value <= 6 -> Color.parseColor("#FFC93C")
                else -> Color.parseColor("#00E0B8")
            }
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val pad = ring.strokeWidth / 2f + 2f
        val r = size / 2f - pad
        rect.set(cx - r, cy - r, cx + r, cy + r)

        canvas.drawCircle(cx, cy, r, track)
        canvas.drawArc(rect, -90f, 360f * progress, false, ring)

        text.textSize = size * 0.34f
        val ty = cy - (text.descent() + text.ascent()) / 2f
        canvas.drawText(secondsLeft.toString(), cx, ty, text)
    }
}
