package ru.readysquad.kvest.util

import android.app.Activity
import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import ru.readysquad.kvest.R

/**
 * Палитры приложения: пользователь выбирает акцентный цвет в настройках.
 * Меняются только акцентные токены Material 3, поверхности и текст остаются как есть.
 */
object Palettes {

    class Item(
        val key: Int,
        val title: String,
        @ColorRes val swatch: Int,
        @StyleRes val theme: Int?
    )

    val all: List<Item> = listOf(
        Item(0, "Фиолетовый", R.color.pal_violet_primary, null),
        Item(1, "Изумрудный", R.color.pal_emerald_primary, R.style.ThemeKvestEmerald),
        Item(2, "Океан", R.color.pal_ocean_primary, R.style.ThemeKvestOcean),
        Item(3, "Янтарь", R.color.pal_amber_primary, R.style.ThemeKvestAmber),
        Item(4, "Роза", R.color.pal_rose_primary, R.style.ThemeKvestRose)
    )

    /** Тема выбранной палитры. Для базовой палитры тема не нужна. */
    @StyleRes
    fun themeFor(key: Int): Int? = all.firstOrNull { it.key == key }?.theme

    /** Применить палитру к экрану. Вызывать в onCreate до setContentView. */
    fun apply(activity: Activity, key: Int) {
        themeFor(key)?.let { activity.setTheme(it) }
    }

    fun titleOf(key: Int): String = all.firstOrNull { it.key == key }?.title ?: all.first().title
}
