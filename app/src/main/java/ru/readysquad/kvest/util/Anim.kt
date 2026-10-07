package ru.readysquad.kvest.util

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** Набор небольших анимаций, из которых собирается «живой» интерфейс. */
object Anim {

    /** Появление снизу с задержкой — используется для «каскада» карточек. */
    fun fadeInUp(view: View, delay: Long = 0L, distance: Float = 48f) {
        view.alpha = 0f
        view.translationY = distance
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(420L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun pop(view: View, delay: Long = 0L) {
        view.scaleX = 0.6f
        view.scaleY = 0.6f
        view.alpha = 0f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setStartDelay(delay)
            .setDuration(380L)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
    }

    fun press(view: View) {
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90L).start()
    }

    fun release(view: View) {
        view.animate().scaleX(1f).scaleY(1f).setDuration(140L).setInterpolator(OvershootInterpolator()).start()
    }

    fun shake(view: View) {
        val a = ValueAnimator.ofFloat(0f, -14f, 14f, -10f, 10f, -5f, 0f)
        a.duration = 420L
        a.addUpdateListener { view.translationX = it.animatedValue as Float }
        a.start()
    }

    fun countUp(from: Int, to: Int, duration: Long = 900L, onTick: (Int) -> Unit) {
        val a = ValueAnimator.ofInt(from, to)
        a.duration = duration
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { onTick(it.animatedValue as Int) }
        a.start()
    }

    /** Пружинное «дыхание» элемента, например кнопки после нажатия. */
    fun springScale(view: View, to: Float = 1.04f, start: Float = 1f) {
        view.scaleX = start
        view.scaleY = start
        SpringAnimation(view, DynamicAnimation.SCALE_X, to)
            .setSpring(SpringForce(to).setDampingRatio(0.35f).setStiffness(SpringForce.STIFFNESS_LOW))
            .start()
        SpringAnimation(view, DynamicAnimation.SCALE_Y, to)
            .setSpring(SpringForce(to).setDampingRatio(0.35f).setStiffness(SpringForce.STIFFNESS_LOW))
            .start()
    }
}
