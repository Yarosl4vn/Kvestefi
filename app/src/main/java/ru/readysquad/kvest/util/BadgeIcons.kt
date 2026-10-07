package ru.readysquad.kvest.util

import ru.readysquad.kvest.R

/**
 * Нарисованные иконки достижений.
 *
 * Раньше награды показывались эмодзи, но часть символов отсутствует в шрифтах
 * на телефонах, и бейджи выглядели пустыми квадратами. Теперь у каждого кода
 * достижения своя векторная иконка, она рисуется всегда и перекрашивается темой.
 */
object BadgeIcons {

    fun of(code: String): Int = when (code) {
        "first_step" -> R.drawable.ic_badge_start
        "streak3", "streak5", "streak7" -> R.drawable.ic_badge_flame
        "sniper" -> R.drawable.ic_badge_target
        "speedrun" -> R.drawable.ic_badge_bolt
        "theory" -> R.drawable.ic_badge_bulb
        "hundred" -> R.drawable.ic_badge_star
        "marathoner" -> R.drawable.ic_badge_cup
        "polymath" -> R.drawable.ic_badge_cap
        "hardworker" -> R.drawable.ic_badge_books
        "genius" -> R.drawable.ic_badge_gem
        "nightowl" -> R.drawable.ic_badge_moon
        "shield" -> R.drawable.ic_badge_shield
        else -> R.drawable.ic_badge_check
    }
}
