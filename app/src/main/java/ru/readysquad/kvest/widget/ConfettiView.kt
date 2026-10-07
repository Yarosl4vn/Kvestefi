package ru.readysquad.kvest.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

/** Лёгкое конфетти для экрана результатов: частицы падают, покачиваются и вращаются. */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val size: Float,
        var angle: Float,
        val spin: Float,
        val color: Int
    )

    private val particles = ArrayList<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var time = 0f

    private val palette = intArrayOf(
        Color.parseColor("#7C5CFF"), Color.parseColor("#00E0B8"),
        Color.parseColor("#FFC93C"), Color.parseColor("#FF5C8A"),
        Color.parseColor("#FF7A59")
    )

    private val driver = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600L
        interpolator = LinearInterpolator()
        addUpdateListener {
            val fraction = it.animatedValue as Float
            step(fraction)
            invalidate()
        }
    }

    /** Запустить салют. */
    fun burst(count: Int = 90) {
        if (width == 0) return
        particles.clear()
        repeat(count) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    y = -Random.nextFloat() * height * 0.6f - 20f,
                    vx = (Random.nextFloat() - 0.5f) * 6f,
                    vy = 6f + Random.nextFloat() * 9f,
                    size = 10f + Random.nextFloat() * 16f,
                    angle = Random.nextFloat() * 360f,
                    spin = (Random.nextFloat() - 0.5f) * 18f,
                    color = palette[Random.nextInt(palette.size)]
                )
            )
        }
        time = 0f
        driver.cancel()
        driver.start()
    }

    private fun step(fraction: Float) {
        time += 0.016f
        for (p in particles) {
            p.vy += 0.35f
            p.x += p.vx + sin(time * 3f + p.size) * 0.8f
            p.y += p.vy
            p.angle += p.spin
        }
        paint.alpha = if (fraction > 0.75f) {
            ((1f - fraction) / 0.25f * 255).toInt().coerceIn(0, 255)
        } else {
            255
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (p in particles) {
            if (p.y > height + 40f) continue
            paint.color = p.color
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.angle)
            rect.set(-p.size / 2f, -p.size / 4f, p.size / 2f, p.size / 4f)
            canvas.drawRoundRect(rect, 4f, 4f, paint)
            canvas.restore()
        }
    }
}
