package ru.readysquad.kvest.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

/** Настройки профиля и локальные рекорды. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("kvest_prefs", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var name: String
        get() = sp.getString("name", "Игрок") ?: "Игрок"
        set(v) = sp.edit().putString("name", v).apply()

    var avatar: String
        get() = sp.getString("avatar", "🧑‍🚀") ?: "🧑‍🚀"
        set(v) = sp.edit().putString("avatar", v).apply()

    var goalPerDay: Int
        get() = sp.getInt("goal_per_day", 3)
        set(v) = sp.edit().putInt("goal_per_day", v).apply()

    var focusTopics: Set<String>
        get() = sp.getStringSet("focus", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("focus", v).apply()

    /** Выбранные предметы подготовки. */
    var chosenSubjects: Set<String>
        get() = sp.getStringSet("subjects", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("subjects", v).apply()

    /** Темы, отмеченные как повторённые в разделе «Обучение». */
    var learnedTopics: Set<String>
        get() = sp.getStringSet("learned_topics", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("learned_topics", v).apply()

    /** Отметить тему повторённой, вернуть новое количество. */
    fun markLearned(topic: String): Int {
        val set = learnedTopics.toMutableSet()
        set.add(topic)
        learnedTopics = set
        return set.size
    }

    /** Открытые достижения, которые пользователь уже посмотрел. */
    var seenBadges: Set<String>
        get() = sp.getStringSet("seen_badges", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("seen_badges", v).apply()

    /** Когда последний раз открывали уведомления: до этой отметки держим метку «новое». */
    var notifSeenAt: Long
        get() = sp.getLong("notif_seen_at", 0L)
        set(v) = sp.edit().putLong("notif_seen_at", v).apply()

    /** Пол из приветствия: влияет только на род в подписях. */
    var gender: String
        get() = sp.getString("gender", "m") ?: "m"
        set(v) = sp.edit().putString("gender", v).apply()

    /**
     * Что открывать в разделе «Обучение» первым:
     * [LEARN_STEPIK] — курсы Stepik, [LEARN_WIKI] — статьи Википедии.
     */
    var learnSource: String
        get() = sp.getString("learn_source", LEARN_STEPIK) ?: LEARN_STEPIK
        set(v) = sp.edit().putString("learn_source", v).apply()

    var bestStreak: Int
        get() = sp.getInt("best_streak", 0)
        set(v) = sp.edit().putInt("best_streak", v).apply()

    // ---- серия дней подряд («огонёк») ----

    /** Текущая серия дней с хотя бы одним квестом. */
    var streak: Int
        get() = sp.getInt("day_streak", 0)
        set(v) = sp.edit().putInt("day_streak", v).apply()

    /** Лучшая серия за всё время. */
    var bestDayStreak: Int
        get() = sp.getInt("day_streak_best", 0)
        set(v) = sp.edit().putInt("day_streak_best", v).apply()

    /** Последний день активности в формате yyyy-MM-dd. */
    var lastActiveDay: String
        get() = sp.getString("last_active_day", "") ?: ""
        set(v) = sp.edit().putString("last_active_day", v).apply()

    /** Дни активности — для полоски недели. */
    var activeDays: Set<String>
        get() = sp.getStringSet("active_days", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("active_days", v).apply()

    fun addActiveDay(day: String) {
        val set = activeDays.toMutableSet()
        set.add(day)
        // держим только последние 60 дней
        if (set.size > 60) {
            val trimmed = set.sorted().takeLast(60).toSet()
            activeDays = trimmed
        } else {
            activeDays = set
        }
    }

    var themeMode: Int
        get() = sp.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)
        set(v) {
            sp.edit().putInt("theme_mode", v).apply()
            AppCompatDelegate.setDefaultNightMode(v)
        }

    /** Сохранить тему без немедленного применения (чтобы не пересоздавать экран). */
    fun saveThemeMode(mode: Int) {
        sp.edit().putInt("theme_mode", mode).apply()
    }

    var soundHints: Boolean
        get() = sp.getBoolean("sound_hints", true)
        set(v) = sp.edit().putBoolean("sound_hints", v).apply()

    /** Выбранная палитра приложения (индекс из Palettes.all). */
    var palette: Int
        get() = sp.getInt("palette", 0)
        set(v) = sp.edit().putInt("palette", v).apply()

    /** Имя файла своей иконки профиля во внутреннем хранилище. */
    var avatarImage: String?
        get() = sp.getString("avatar_image", null)
        set(v) = sp.edit().putString("avatar_image", v).apply()

    // ---- ежедневная цель ----
    private fun today(): String {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR).toString() + "-" + (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH)
    }

    var todayQuests: Int
        get() = if (sp.getString("today_date", "") == today()) sp.getInt("today_quests", 0) else 0
        set(v) = sp.edit().putString("today_date", today()).putInt("today_quests", v).apply()

    fun bumpToday() {
        todayQuests = todayQuests + 1
    }

    companion object {
        const val LEARN_STEPIK = "stepik"
        const val LEARN_WIKI = "wiki"
    }
}
