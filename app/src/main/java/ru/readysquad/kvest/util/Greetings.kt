package ru.readysquad.kvest.util

/**
 * Формулировки приветствия с учётом пола из профиля.
 *
 * Пол влияет только на род в подписях, чтобы текст не звучал машинно:
 * «ты готов» и «ты готова», «решил» и «решила».
 */
object Greetings {

    const val MALE = "m"
    const val FEMALE = "f"

    private fun isFemale(gender: String?) = gender == FEMALE

    fun ready(gender: String?): String = if (isFemale(gender)) "Готова" else "Готов"

    fun solved(gender: String?): String = if (isFemale(gender)) "решила" else "решил"

    /** Заголовок приветствия при входе. */
    fun helloTitle(name: String, gender: String?): String {
        val who = name.trim().ifBlank { "друг" }
        return if (isFemale(gender)) "С возвращением, $who!" else "С возвращением, $who!"
    }

    /** Подпись под заголовком: зависит от того, сколько уже пройдено. */
    fun helloText(gender: String?, quests: Int, streakDays: Int): String = when {
        quests == 0 -> "Первый квест ждёт. ${ready(gender)} начать?"
        streakDays > 1 -> "Серия продолжается — не разрывай её сегодня."
        else -> "${ready(gender)} решать дальше?"
    }

    /** Строка с серией дней. */
    fun streakLine(streakDays: Int, bestStreak: Int): String = when {
        streakDays <= 0 && bestStreak <= 0 -> "Серия пока пустая: реши одну задачу — и она начнётся."
        streakDays <= 0 -> "Серия прервалась. Лучшая была $bestStreak дней — начнём заново?"
        streakDays == 1 -> "Серия: 1 день. Лучшая: $bestStreak."
        else -> "Серия: $streakDays дней подряд. Лучшая: $bestStreak."
    }
}
