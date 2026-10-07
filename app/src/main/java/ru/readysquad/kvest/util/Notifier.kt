package ru.readysquad.kvest.util

import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Stats
import ru.readysquad.kvest.data.TopicStat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Уведомления приложения: что произошло и на что обратить внимание.
 *
 * Список собирается из состояния профиля, а не хранится отдельно: так он всегда
 * совпадает с реальными данными. Метка «есть новое» держится до первого открытия
 * уведомлений в текущие сутки.
 */
object Notifier {

    data class Item(val iconRes: Int, val title: String, val text: String, val warn: Boolean)

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale("ru"))

    fun todayKey(): String = dayFmt.format(Date())

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Достижения, которые открылись, но их ещё не видели. */
    fun newBadges(prefs: Prefs, badges: List<Badge>): List<Badge> {
        val seen = prefs.seenBadges
        return badges.filter { it.isUnlocked && !seen.contains(it.code) }
    }

    fun build(
        prefs: Prefs,
        stats: Stats,
        badges: List<Badge>,
        topics: List<TopicStat>
    ): List<Item> {
        val out = ArrayList<Item>()

        newBadges(prefs, badges).take(3).forEach { b ->
            out.add(
                Item(
                    BadgeIcons.of(b.code),
                    "Новое достижение: ${b.title}",
                    b.description,
                    warn = false
                )
            )
        }

        if (stats.quests == 0) {
            out.add(
                Item(
                    R.drawable.ic_card_rocket,
                    "Первый квест ждёт",
                    "Пять заданий по одному предмету — это займёт около четырёх минут.",
                    warn = false
                )
            )
        }

        val today = prefs.todayQuests
        val goal = prefs.goalPerDay.coerceAtLeast(1)
        out.add(
            Item(
                R.drawable.ic_badge_target,
                if (today >= goal) "Цель на сегодня выполнена" else "Цель на сегодня: $today из $goal",
                if (today >= goal) "Отличный день. Можно пройти ещё один квест для серии."
                else "Пройди ещё ${goal - today} квест — и цель дня закрыта.",
                warn = false
            )
        )

        val active = prefs.activeDays.contains(todayKey())
        if (prefs.streak > 0 || active) {
            out.add(
                Item(
                    R.drawable.ic_badge_flame,
                    if (active) "Серия ${prefs.streak} дней продолжается"
                    else "Серия ${prefs.streak} дней под угрозой",
                    if (active) "Ты уже занимался сегодня — серия в безопасности."
                    else "Реши хотя бы один квест сегодня, иначе серия сбросится.",
                    warn = !active
                )
            )
        }

        val weak = topics.filter { it.total >= 2 && it.accuracy < 60 }.take(2)
        weak.forEach { t ->
            out.add(
                Item(
                    R.drawable.ic_card_chart,
                    "Слабая тема: ${t.topic}",
                    "Верных ответов ${t.correct} из ${t.total}. Загляни в шпаргалки по этой теме.",
                    warn = true
                )
            )
        }

        if (stats.total > 0 && stats.accuracy >= 80) {
            out.add(
                Item(
                    R.drawable.ic_badge_check,
                    "Точность ${stats.accuracy}%",
                    "Такой темп близок к пятёрке на экзамене. Продолжай.",
                    warn = false
                )
            )
        }

        return out
    }

    /** Показывать ли красную метку на колокольчике. */
    fun hasUnread(prefs: Prefs, items: List<Item>): Boolean =
        items.isNotEmpty() && prefs.notifSeenAt < startOfToday()

    /** Отмечает уведомления и открытые достижения просмотренными. */
    fun markSeen(prefs: Prefs, badges: List<Badge>) {
        prefs.notifSeenAt = System.currentTimeMillis()
        markBadgesSeen(prefs, badges)
    }

    /** Отмечает только достижения: уведомления остаются непрочитанными. */
    fun markBadgesSeen(prefs: Prefs, badges: List<Badge>) {
        prefs.seenBadges = prefs.seenBadges + badges.filter { it.isUnlocked }.map { it.code }
    }
}
