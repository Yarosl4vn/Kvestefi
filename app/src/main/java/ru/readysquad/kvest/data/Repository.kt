package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

/** Единая точка доступа к локальной БД. */
class Repository private constructor(private val helper: DbHelper) {

    private val db get() = helper.writableDatabase

    /** Источник заданий (см. [TaskSource]). */
    val source: TaskSource by lazy { OfflineTaskSource(this) }

    // ------------------------------------------------------------------ subjects

    fun subjects(): List<Subject> {
        val out = ArrayList<Subject>()
        db.rawQuery("SELECT code, title, emoji, color_from, color_to, topics FROM subjects", null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Subject(
                        c.getString(0), c.getString(1), c.getString(2),
                        c.getString(3) ?: "#7C5CFF", c.getString(4) ?: "#3B2FA8", c.getString(5) ?: ""
                    )
                )
            }
        }
        return out
    }

    fun subject(code: String): Subject? = subjects().firstOrNull { it.code == code }

    fun taskCount(subjectCode: String): Int {
        db.rawQuery("SELECT COUNT(*) FROM tasks WHERE subject_code = ?", arrayOf(subjectCode)).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** Задачи предмета в случайном порядке. */
    fun shuffledTasks(subjectCode: String, limit: Int): List<Task> =
        queryTasks(
            "SELECT id, subject_code, topic, exam, difficulty, question, opt1, opt2, opt3, opt4, " +
                "correct_index, explanation FROM tasks WHERE subject_code = ? " +
                "ORDER BY RANDOM() LIMIT ?",
            arrayOf(subjectCode, limit.toString())
        )

    /**
     * Чтение задач. Колонки берём по имени, а не по позиции, —
     * иначе при изменении списка полей в SELECT значения съезжают.
     */
    private fun queryTasks(sql: String, args: Array<String>?): List<Task> {
        val out = ArrayList<Task>()
        db.rawQuery(sql, args).use { c ->
            val qId = c.getColumnIndexOrThrow("id")
            val qSubject = c.getColumnIndexOrThrow("subject_code")
            val qTopic = c.getColumnIndexOrThrow("topic")
            val qExam = c.getColumnIndexOrThrow("exam")
            val qDifficulty = c.getColumnIndexOrThrow("difficulty")
            val qQuestion = c.getColumnIndexOrThrow("question")
            val qOpts = (1..4).map { c.getColumnIndexOrThrow("opt$it") }
            val qCorrect = c.getColumnIndexOrThrow("correct_index")
            val qExplanation = c.getColumnIndexOrThrow("explanation")

            while (c.moveToNext()) {
                val raw = qOpts.mapNotNull { c.getString(it) }

                // Перемешиваем варианты при каждой выдаче: правильный ответ не должен
                // всегда оказываться под одной и той же буквой.
                val order = raw.indices.shuffled()
                val options = order.map { raw[it] }
                val correct = order.indexOf(c.getInt(qCorrect)).coerceAtLeast(0)

                out.add(
                    Task(
                        id = c.getLong(qId),
                        subjectCode = c.getString(qSubject),
                        topic = c.getString(qTopic) ?: "",
                        exam = c.getString(qExam) ?: Exam.OGE,
                        difficulty = c.getInt(qDifficulty),
                        question = c.getString(qQuestion),
                        options = options,
                        correctIndex = correct,
                        explanation = c.getString(qExplanation) ?: ""
                    )
                )
            }
        }
        return out
    }

    // ------------------------------------------------------------------ theory

    /**
     * Сохраняет шпаргалку, найденную в открытом источнике, чтобы она осталась
     * в списке и после перезапуска приложения.
     *
     * @return true, если запись добавилась
     */
    fun addTheory(subjectCode: String, topic: String, exam: String, teaser: String, body: String): Boolean {
        val values = ContentValues().apply {
            put("subject_code", subjectCode)
            put("topic", topic)
            put("exam", exam)
            put("teaser", teaser)
            put("body", body)
        }
        return db.insert("theory", null, values) > 0
    }

    /** Карточки теории с фильтром по предмету и типу экзамена. */
    fun theory(subjectCode: String? = null, exam: String? = null): List<TheoryCard> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (subjectCode != null) {
            where.add("t.subject_code = ?")
            args.add(subjectCode)
        }
        if (exam != null) {
            where.add("t.exam = ?")
            args.add(exam)
        }
        val sql = StringBuilder(
            "SELECT t.id, t.subject_code, s.title, s.emoji, t.topic, t.exam, t.teaser, t.body " +
                "FROM theory t LEFT JOIN subjects s ON s.code = t.subject_code"
        )
        if (where.isNotEmpty()) sql.append(" WHERE ").append(where.joinToString(" AND "))
        sql.append(" ORDER BY t.subject_code, t.id")

        val out = ArrayList<TheoryCard>()
        db.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                out.add(
                    TheoryCard(
                        id = c.getLong(0),
                        subjectCode = c.getString(1) ?: "",
                        subjectTitle = c.getString(2) ?: "",
                        subjectEmoji = c.getString(3) ?: "📘",
                        topic = c.getString(4) ?: "",
                        exam = c.getString(5) ?: Exam.OGE,
                        teaser = c.getString(6) ?: "",
                        body = c.getString(7) ?: ""
                    )
                )
            }
        }
        return out
    }

    // ------------------------------------------------------------------ attempts

    fun saveAttempt(
        subjectCode: String,
        correct: Int,
        total: Int,
        score: Int,
        grade: Int,
        seconds: Int,
        answers: List<AnswerRow>
    ): Long {
        val now = System.currentTimeMillis()
        val attemptId = db.insert("attempts", null, ContentValues().apply {
            put("subject_code", subjectCode)
            put("correct", correct)
            put("total", total)
            put("score", score)
            put("grade", grade)
            put("seconds", seconds)
            put("created_at", now)
        })
        answers.forEach { a ->
            db.insert("answers", null, ContentValues().apply {
                put("attempt_id", attemptId)
                put("task_id", a.taskId)
                put("topic", a.topic)
                put("is_correct", if (a.isCorrect) 1 else 0)
                put("seconds", a.seconds)
                put("question", a.question)
                put("typed", a.typed)
                put("expected", a.expected)
            })
        }
        return attemptId
    }

    /**
     * Ответы внутри попытки: вопрос, что ответил игрок и что было верно.
     * Нужно, чтобы в истории можно было открыть попытку и посмотреть задания.
     */
    fun attemptAnswers(attemptId: Long): List<AnswerRow> {
        val out = ArrayList<AnswerRow>()
        db.rawQuery(
            "SELECT task_id, topic, is_correct, seconds, question, typed, expected " +
                "FROM answers WHERE attempt_id = ? ORDER BY id",
            arrayOf(attemptId.toString())
        ).use { c ->
            val iTask = c.getColumnIndexOrThrow("task_id")
            val iTopic = c.getColumnIndexOrThrow("topic")
            val iOk = c.getColumnIndexOrThrow("is_correct")
            val iSec = c.getColumnIndexOrThrow("seconds")
            val iQ = c.getColumnIndexOrThrow("question")
            val iTyped = c.getColumnIndexOrThrow("typed")
            val iExp = c.getColumnIndexOrThrow("expected")
            while (c.moveToNext()) {
                out.add(
                    AnswerRow(
                        taskId = c.getLong(iTask),
                        topic = c.getString(iTopic) ?: "",
                        isCorrect = c.getInt(iOk) == 1,
                        seconds = c.getInt(iSec),
                        question = c.getString(iQ) ?: "",
                        typed = c.getString(iTyped) ?: "",
                        expected = c.getString(iExp) ?: ""
                    )
                )
            }
        }
        return out
    }

    fun attempts(limit: Int = 60): List<Attempt> {
        val out = ArrayList<Attempt>()
        db.rawQuery(
            "SELECT a.id, a.subject_code, s.title, s.emoji, a.correct, a.total, a.score, a.grade, a.seconds, a.created_at " +
                "FROM attempts a LEFT JOIN subjects s ON s.code = a.subject_code " +
                "ORDER BY a.created_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(cursorToAttempt(c))
        }
        return out
    }

    fun lastAttempt(): Attempt? {
        db.rawQuery(
            "SELECT a.id, a.subject_code, s.title, s.emoji, a.correct, a.total, a.score, a.grade, a.seconds, a.created_at " +
                "FROM attempts a LEFT JOIN subjects s ON s.code = a.subject_code " +
                "ORDER BY a.created_at DESC LIMIT 1", null
        ).use { c ->
            return if (c.moveToFirst()) cursorToAttempt(c) else null
        }
    }

    /** Лучший результат по каждому предмету. */
    fun bestScores(): Map<String, Int> {
        val out = HashMap<String, Int>()
        db.rawQuery("SELECT subject_code, MAX(score) FROM attempts GROUP BY subject_code", null).use { c ->
            while (c.moveToNext()) out[c.getString(0) ?: ""] = c.getInt(1)
        }
        return out
    }

    /** Сколько раз проходили предмет. */
    fun questsBySubject(): Map<String, Int> {
        val out = HashMap<String, Int>()
        db.rawQuery("SELECT subject_code, COUNT(*) FROM attempts GROUP BY subject_code", null).use { c ->
            while (c.moveToNext()) out[c.getString(0) ?: ""] = c.getInt(1)
        }
        return out
    }

    private fun cursorToAttempt(c: Cursor) = Attempt(
        id = c.getLong(0),
        subjectCode = c.getString(1) ?: "",
        subjectTitle = c.getString(2) ?: c.getString(1) ?: "",
        subjectEmoji = c.getString(3) ?: "📘",
        correct = c.getInt(4),
        total = c.getInt(5),
        score = c.getInt(6),
        grade = c.getInt(7),
        seconds = c.getInt(8),
        createdAt = c.getLong(9)
    )

    /** История для графика: от старых попыток к новым. */
    fun progress(): List<ProgressPoint> {
        val raw = ArrayList<Attempt>()
        db.rawQuery(
            "SELECT a.id, a.subject_code, s.title, s.emoji, a.correct, a.total, a.score, a.grade, a.seconds, a.created_at " +
                "FROM attempts a LEFT JOIN subjects s ON s.code = a.subject_code " +
                "ORDER BY a.created_at ASC LIMIT 30", null
        ).use { c ->
            while (c.moveToNext()) raw.add(cursorToAttempt(c))
        }
        val out = ArrayList<ProgressPoint>(raw.size)
        raw.forEachIndexed { i, a ->
            out.add(ProgressPoint(i + 1, a.score, a.accuracy, TimeFmt.short(a.createdAt)))
        }
        return out
    }

    // ------------------------------------------------------------------ stats

    fun stats(): Stats {
        var totalScore = 0; var quests = 0; var correct = 0; var total = 0
        var best = 0; var seconds = 0; var distinct = 0
        db.rawQuery(
            "SELECT COALESCE(SUM(score),0), COUNT(*), COALESCE(SUM(correct),0), COALESCE(SUM(total),0), " +
                "COALESCE(MAX(score),0), COALESCE(SUM(seconds),0), COUNT(DISTINCT subject_code) FROM attempts", null
        ).use { c ->
            if (c.moveToFirst()) {
                totalScore = c.getInt(0); quests = c.getInt(1); correct = c.getInt(2)
                total = c.getInt(3); best = c.getInt(4); seconds = c.getInt(5); distinct = c.getInt(6)
            }
        }
        return Stats(
            totalScore = totalScore,
            quests = quests,
            correct = correct,
            total = total,
            bestScore = best,
            distinctSubjects = distinct,
            minutes = seconds / 60
        )
    }

    fun topicStats(subjectCode: String? = null): List<TopicStat> {
        val out = ArrayList<TopicStat>()
        val sql = StringBuilder("SELECT t.topic, SUM(t.is_correct), COUNT(*) FROM answers t ")
        var args: Array<String>? = null
        if (subjectCode != null) {
            sql.append("JOIN attempts a ON a.id = t.attempt_id WHERE a.subject_code = ? GROUP BY t.topic")
            args = arrayOf(subjectCode)
        } else {
            sql.append("GROUP BY t.topic")
        }
        db.rawQuery(sql.toString(), args).use { c ->
            while (c.moveToNext()) out.add(TopicStat(c.getString(0) ?: "—", c.getInt(1), c.getInt(2)))
        }
        out.sortBy { it.accuracy }
        return out
    }

    // ------------------------------------------------------------------ badges

    fun badges(): List<Badge> {
        val out = ArrayList<Badge>()
        db.rawQuery("SELECT code, emoji, title, description, unlocked_at FROM badges", null).use { c ->
            while (c.moveToNext()) {
                val at = if (c.isNull(4)) null else c.getLong(4)
                out.add(Badge(c.getString(0), c.getString(1) ?: "🏅", c.getString(2) ?: "", c.getString(3) ?: "", at))
            }
        }
        return out
    }

    fun unlockedCount(): Int {
        db.rawQuery("SELECT COUNT(*) FROM badges WHERE unlocked_at IS NOT NULL", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** Выдать бейдж. true — если он выдан впервые (нужно показать анимацию). */
    fun unlockBadge(code: String): Boolean {
        val now = System.currentTimeMillis()
        db.rawQuery("SELECT unlocked_at FROM badges WHERE code = ?", arrayOf(code)).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return false
        }
        db.execSQL("UPDATE badges SET unlocked_at = ? WHERE code = ?", arrayOf<Any>(now, code))
        return true
    }

    /** Полная очистка прогресса с повторным наполнением банка заданий. */
    fun resetAll() {
        listOf("answers", "attempts", "tasks", "theory", "badges", "subjects").forEach {
            db.execSQL("DROP TABLE IF EXISTS " + it)
        }
        helper.onCreate(db)
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository {
            return instance ?: synchronized(this) {
                instance ?: Repository(DbHelper(context)).also { instance = it }
            }
        }
    }
}
