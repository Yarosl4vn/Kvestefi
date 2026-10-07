package ru.readysquad.kvest.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Локальная база SQLite: банк заданий, теория, история попыток и бейджи.
 * Внешний сервер не нужен — приложение полностью работает офлайн.
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
        seedDatabase(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Совсем старые сборки проще пересобрать: в них нет части таблиц.
        if (oldVersion < 6) {
            dropAll(db)
            createSchema(db)
            seedDatabase(db)
            return
        }

        // Дальше обновляем по шагам и без потери прогресса: миграции только
        // добавляют нужные колонки и убирают то, чего больше нет.
        var v = oldVersion
        if (v < 7) {
            // 6 -> 7: раздел «Бланк ответов ФИПИ» убран, бейдж за него больше не получить
            runCatching { db.execSQL("DELETE FROM badges WHERE code = 'blank_ace'") }
            v = 7
        }
        if (v < 8) {
            // 7 -> 8: в истории попыток храним сам вопрос, ответ игрока и верный ответ
            runCatching { db.execSQL("ALTER TABLE answers ADD COLUMN question TEXT") }
            runCatching { db.execSQL("ALTER TABLE answers ADD COLUMN typed TEXT") }
            runCatching { db.execSQL("ALTER TABLE answers ADD COLUMN expected TEXT") }
            v = 8
        }
    }

    private fun dropAll(db: SQLiteDatabase) {
        listOf("answers", "attempts", "tasks", "theory", "badges", "subjects").forEach {
            db.execSQL("DROP TABLE IF EXISTS " + it)
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE subjects (" +
                "code TEXT PRIMARY KEY, title TEXT NOT NULL, emoji TEXT, " +
                "color_from TEXT, color_to TEXT, topics TEXT)"
        )
        db.execSQL(
            "CREATE TABLE tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, subject_code TEXT NOT NULL, topic TEXT, " +
                "exam TEXT, difficulty INTEGER DEFAULT 1, question TEXT NOT NULL, " +
                "opt1 TEXT, opt2 TEXT, opt3 TEXT, opt4 TEXT, " +
                "correct_index INTEGER NOT NULL, explanation TEXT)"
        )
        db.execSQL(
            "CREATE TABLE theory (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, subject_code TEXT NOT NULL, topic TEXT, " +
                "exam TEXT, teaser TEXT, body TEXT)"
        )
        db.execSQL(
            "CREATE TABLE attempts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, subject_code TEXT NOT NULL, " +
                "correct INTEGER NOT NULL, total INTEGER NOT NULL, score INTEGER NOT NULL, " +
                "grade INTEGER NOT NULL, seconds INTEGER NOT NULL, created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE answers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, attempt_id INTEGER NOT NULL, " +
                "task_id INTEGER, topic TEXT, is_correct INTEGER NOT NULL, seconds INTEGER NOT NULL, " +
                "question TEXT, typed TEXT, expected TEXT)"
        )
        db.execSQL(
            "CREATE TABLE badges (" +
                "code TEXT PRIMARY KEY, emoji TEXT, title TEXT, description TEXT, unlocked_at INTEGER)"
        )
        db.execSQL("CREATE INDEX idx_answers_attempt ON answers(attempt_id)")
        db.execSQL("CREATE INDEX idx_tasks_subject ON tasks(subject_code)")
        db.execSQL("CREATE INDEX idx_theory_subject ON theory(subject_code)")
    }

    private fun seedDatabase(db: SQLiteDatabase) {
        SeedData.subjects.forEach { s ->
            db.insert("subjects", null, ContentValues().apply {
                put("code", s.code)
                put("title", s.title)
                put("emoji", s.emoji)
                put("color_from", s.colorFrom)
                put("color_to", s.colorTo)
                put("topics", s.topics)
            })
        }

        SeedData.tasks.forEach { t ->
            db.insert("tasks", null, ContentValues().apply {
                put("subject_code", t.subjectCode)
                put("topic", t.topic)
                put("exam", t.exam)
                put("difficulty", t.difficulty)
                put("question", t.question)
                put("opt1", t.options.getOrNull(0))
                put("opt2", t.options.getOrNull(1))
                put("opt3", t.options.getOrNull(2))
                put("opt4", t.options.getOrNull(3))
                put("correct_index", t.correctIndex)
                put("explanation", t.explanation)
            })
        }

        (SeedTheory.cards + SeedTheoryExtra.cards).forEach { c ->
            db.insert("theory", null, ContentValues().apply {
                put("subject_code", c.subjectCode)
                put("topic", c.topic)
                put("exam", c.exam)
                put("teaser", c.teaser)
                put("body", c.body)
            })
        }

        SeedData.badges.forEach { b ->
            db.insert("badges", null, ContentValues().apply {
                put("code", b.code)
                put("emoji", b.emoji)
                put("title", b.title)
                put("description", b.description)
                putNull("unlocked_at")
            })
        }

    }

    companion object {
        const val NAME = "kvest_marathon.db"
        const val VERSION = 8
    }
}
