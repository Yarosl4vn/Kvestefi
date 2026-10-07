package ru.readysquad.kvest.util

import ru.readysquad.kvest.data.Prefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Серия дней подряд («огонёк»): заходишь каждый день и решаешь хотя бы один квест —
 * серия растёт. Пропустил день — счётчик начинается заново.
 */
object Streaks {

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private const val DAY_MS = 86_400_000L

    fun today(): String = fmt.format(Date())

    private fun yesterday(): String = fmt.format(Date(System.currentTimeMillis() - DAY_MS))

    /** Отметить активность за сегодня и вернуть текущую длину серии. */
    fun touch(prefs: Prefs): Int {
        val today = today()
        if (prefs.lastActiveDay == today) {
            prefs.addActiveDay(today)
            return prefs.streak
        }
        val streak = if (prefs.lastActiveDay == yesterday()) prefs.streak + 1 else 1
        prefs.streak = streak
        prefs.lastActiveDay = today
        prefs.addActiveDay(today)
        return streak
    }

    /** Серия обнулилась? Нужно для подсказки на главной. */
    fun isBroken(prefs: Prefs): Boolean =
        prefs.lastActiveDay.isNotEmpty() && prefs.lastActiveDay != today() && prefs.lastActiveDay != yesterday()

    class Day(val label: String, val active: Boolean, val isToday: Boolean)

    /** Последние семь дней: подпись, была ли активность, сегодняшний день. */
    fun week(prefs: Prefs): List<Day> {
        val active = prefs.activeDays
        val labels = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
        val out = ArrayList<Day>(7)
        val cal = Calendar.getInstance()
        // сдвигаемся к понедельнику текущей недели
        val shift = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_YEAR, -shift)
        for (i in 0 until 7) {
            val key = fmt.format(cal.time)
            val isToday = key == today()
            out.add(Day(labels[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7], active.contains(key), isToday))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }
}
