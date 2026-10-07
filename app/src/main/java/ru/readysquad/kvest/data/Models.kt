package ru.readysquad.kvest.data

/** Тип экзамена, к которому относится задание. */
object Exam {
    const val OGE = "ОГЭ"
    const val EGE = "ЕГЭ"
    const val VPR = "ВПР"
    val all = listOf(OGE, EGE, VPR)
}

/** Учебный предмет, по которому доступны квесты. */
data class Subject(
    val code: String,
    val title: String,
    val emoji: String,
    val colorFrom: String,
    val colorTo: String,
    val topics: String
) : java.io.Serializable

/**
 * Одна задача.
 * Формат — с кратким ответом, как в бланке ФИПИ: ответ вписывается в клеточки.
 * Задачи захардкожены офлайн; реальный источник (банк ФИПИ или ИИ) подключается
 * через [TaskSource] и пишет результат в ту же таблицу tasks.
 */
data class Task(
    val id: Long,
    val subjectCode: String,
    val topic: String,
    val exam: String,
    val difficulty: Int,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    /** Ссылка на рисунок к заданию, если он есть (онлайн-источник). */
    val imageUrl: String? = null,
    /** Откуда взято задание: показывается пользователю как ссылка на источник. */
    val source: String = ""
) {
    /** Текст правильного ответа — его вписывают в бланк. */
    val answer: String get() = options.getOrElse(correctIndex) { "" }
}

/** Итог одной попытки (одного «квеста»). */
data class Attempt(
    val id: Long,
    val subjectCode: String,
    val subjectTitle: String,
    val subjectEmoji: String,
    val correct: Int,
    val total: Int,
    val score: Int,
    val grade: Int,
    val seconds: Int,
    val createdAt: Long
) : java.io.Serializable {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100 / total)
}

/** Достижение / бейдж. */
data class Badge(
    val code: String,
    val emoji: String,
    val title: String,
    val description: String,
    val unlockedAt: Long? = null
) : java.io.Serializable {
    val isUnlocked: Boolean get() = unlockedAt != null
}

/** Сводная статистика игрока. */
data class Stats(
    val totalScore: Int,
    val quests: Int,
    val correct: Int,
    val total: Int,
    val bestScore: Int,
    val distinctSubjects: Int,
    val minutes: Int
) {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100 / total)
}

/**
 * Ответ игрока на конкретную задачу.
 *
 * Хранит не только результат, но и сам вопрос, введённый и верный ответ:
 * без этого в истории попыток нельзя посмотреть, что именно решалось.
 */
data class AnswerRow(
    val taskId: Long,
    val topic: String,
    val isCorrect: Boolean,
    val seconds: Int,
    val question: String = "",
    val typed: String = "",
    val expected: String = ""
)

/** Точка на графике прогресса. */
data class ProgressPoint(
    val index: Int,
    val score: Int,
    val accuracy: Int,
    val label: String
) : java.io.Serializable

/** Слабая/сильная тема. */
data class TopicStat(
    val topic: String,
    val correct: Int,
    val total: Int
) : java.io.Serializable {
    val accuracy: Int get() = if (total == 0) 0 else (correct * 100 / total)
}

/** Карточка теории для раздела «Обучение». */
data class TheoryCard(
    val id: Long,
    val subjectCode: String,
    val subjectTitle: String,
    val subjectEmoji: String,
    val topic: String,
    val exam: String,
    val teaser: String,
    val body: String
) : java.io.Serializable

/** Результат прохождения квеста, передаётся на экран результатов. */
data class QuestResult(
    val subjectCode: String,
    val subjectTitle: String,
    val subjectEmoji: String,
    val correct: Int,
    val total: Int,
    val score: Int,
    val baseScore: Int,
    val speedBonus: Int,
    val streakBonus: Int,
    val grade: Int,
    val seconds: Int,
    val bestStreak: Int,
    val newBadges: List<Badge>,
    val topicsToImprove: List<TopicStat>
) : java.io.Serializable

