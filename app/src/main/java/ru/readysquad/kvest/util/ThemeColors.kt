package ru.readysquad.kvest.util

import android.content.Context
import android.graphics.Color
import android.util.TypedValue

/**
 * Цвета текущей темы. Берём их из атрибутов, а не из ресурсов,
 * чтобы код следовал за выбранной палитрой приложения.
 */
object ThemeColors {

    fun primary(context: Context): Int = resolve(context, ATTR_PRIMARY, DEFAULT_PRIMARY)

    fun onPrimary(context: Context): Int = resolve(context, ATTR_ON_PRIMARY, Color.WHITE)

    fun surfaceContainer(context: Context): Int =
        resolve(context, ATTR_SURFACE_CONTAINER, Color.parseColor("#1D1D23"))

    fun onSurfaceVariant(context: Context): Int =
        resolve(context, ATTR_ON_SURFACE_VARIANT, Color.parseColor("#A9A3AF"))

    fun onSecondaryContainer(context: Context): Int =
        resolve(context, ATTR_ON_SECONDARY_CONTAINER, Color.WHITE)

    private fun resolve(context: Context, attr: Int, fallback: Int): Int {
        if (attr == 0) return fallback
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) value.data else fallback
    }

    private const val DEFAULT_PRIMARY = 0xFF6750A4.toInt()

    // атрибуты Material 3; берём через R библиотеки, чтобы не зависеть от своей разметки
    private val ATTR_PRIMARY = com.google.android.material.R.attr.colorPrimary
    private val ATTR_ON_PRIMARY = com.google.android.material.R.attr.colorOnPrimary
    private val ATTR_SURFACE_CONTAINER = com.google.android.material.R.attr.colorSurfaceContainer
    private val ATTR_ON_SURFACE_VARIANT = com.google.android.material.R.attr.colorOnSurfaceVariant
    private val ATTR_ON_SECONDARY_CONTAINER = com.google.android.material.R.attr.colorOnSecondaryContainer
}
