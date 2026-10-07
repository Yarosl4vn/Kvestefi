package ru.readysquad.kvest.util

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.AnswerRow
import ru.readysquad.kvest.data.Attempt
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Scoring
import ru.readysquad.kvest.data.Stats
import ru.readysquad.kvest.data.TimeFmt
import ru.readysquad.kvest.data.TopicStat

/**
 * Диалоги с подробностями: их открывают карточки главного экрана, история попыток
 * и достижения. Собираются кодом, чтобы не плодить разметку и не разъезжаться с ней.
 */
object Dialogs {

    private fun dp(ctx: Context, v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()

    private fun color(ctx: Context, res: Int) = ContextCompat.getColor(ctx, res)

    /** Прокручиваемая колонка для содержимого диалога. */
    private fun column(ctx: Context): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val pad = dp(ctx, 20)
        box.setPadding(pad, dp(ctx, 8), pad, dp(ctx, 4))
        return box
    }

    private fun scroll(ctx: Context, inner: View): ScrollView {
        val sv = ScrollView(ctx)
        sv.addView(inner, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return sv
    }

    private fun title(ctx: Context, text: String, top: Int = 0): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(ctx, R.color.accent))
            setPadding(0, dp(ctx, top), 0, dp(ctx, 6))
            letterSpacing = 0.06f
        }

    private fun paragraph(
        ctx: Context,
        text: String,
        size: Float = 14f,
        colorRes: Int = R.color.on_bg,
        top: Int = 0,
        bold: Boolean = false
    ): TextView =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(color(ctx, colorRes))
            setPadding(0, dp(ctx, top), 0, dp(ctx, 4))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    /** Строка «слева подпись — справа значение». */
    private fun kv(ctx: Context, key: String, value: String, valueColorRes: Int = R.color.on_bg): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(ctx, 5), 0, dp(ctx, 5))
        row.addView(
            TextView(ctx).apply {
                text = key
                textSize = 14f
                setTextColor(color(ctx, R.color.on_bg_variant))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        row.addView(
            TextView(ctx).apply {
                text = value
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(ctx, valueColorRes))
                gravity = Gravity.END
            }
        )
        return row
    }

    /** Полоса прогресса из двух прямоугольников. */
    private fun bar(ctx: Context, fraction: Float, heightDp: Int = 8): View {
        val holder = FrameLayout(ctx)
        holder.setBackgroundResource(R.drawable.bg_stat)
        val h = dp(ctx, heightDp)
        holder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        val fill = View(ctx)
        fill.setBackgroundResource(R.drawable.bg_pill_primary)
        val f = fraction.coerceIn(0f, 1f)
        holder.addView(fill, FrameLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT))
        holder.post {
            fill.layoutParams = FrameLayout.LayoutParams(
                (holder.width * f).toInt().coerceAtLeast(if (f > 0f) dp(ctx, 6) else 1),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val wrap = LinearLayout(ctx)
        wrap.setPadding(0, dp(ctx, 6), 0, dp(ctx, 10))
        wrap.addView(holder)
        return wrap
    }

    private fun show(ctx: Context, view: View, positive: String = "Понятно") {
        MaterialAlertDialogBuilder(ctx)
            .setView(view)
            .setPositiveButton(positive, null)
            .show()
    }

    // ------------------------------------------------------------------ главный экран

    /** Уровень: сколько очков набрано и что нужно для следующего. */
    fun level(ctx: Context, prefs: Prefs, stats: Stats, onTop: (() -> Unit)? = null) {
        val col = column(ctx)
        val level = Leveling.level(stats.totalScore)
        col.addView(paragraph(ctx, "Уровень $level · ${Leveling.title(level)}", 22f, bold = true))
        col.addView(paragraph(ctx, Leveling.hint(level), 14f, R.color.on_bg_variant, top = 4))
        col.addView(title(ctx, "ПРОГРЕСС", 16))

        val cur = Leveling.startScore(level)
        val next = Leveling.nextScore(level)
        col.addView(
            paragraph(
                ctx,
                "Очков всего: ${stats.totalScore}",
                15f
            )
        )
        col.addView(bar(ctx, (stats.totalScore - cur).toFloat() / (next - cur).toFloat()))
        col.addView(
            paragraph(
                ctx,
                if (level >= Leveling.MAX)
                    "Максимальный уровень достигнут. Дальше — только серия дней."
                else
                    "До уровня ${level + 1} осталось ${next - stats.totalScore} очков",
                14f,
                R.color.accent,
                bold = true
            )
        )
        col.addView(title(ctx, "КАК РАСТУТ ОЧКИ", 14))
        col.addView(paragraph(ctx, "10 очков за верный ответ", 14f))
        col.addView(paragraph(ctx, "+5, если решить быстрее 10 секунд", 14f))
        col.addView(paragraph(ctx, "+5 за каждый верный ответ с третьего подряд", 14f))
        col.addView(
            paragraph(
                ctx,
                "Пройдено квестов: ${stats.quests} · точность ${stats.accuracy}%",
                14f,
                R.color.on_bg_variant,
                top = 8
            )
        )
        val builder = MaterialAlertDialogBuilder(ctx)
            .setView(scroll(ctx, col))
            .setPositiveButton("Понятно", null)
        if (onTop != null) {
            builder.setNegativeButton("Топ по предметам") { _, _ -> onTop() }
        }
        builder.show()
    }

    /** Цель на сегодня и как её закрыть. */
    fun goal(ctx: Context, prefs: Prefs, stats: Stats) {
        val col = column(ctx)
        val done = prefs.todayQuests
        val goal = prefs.goalPerDay.coerceAtLeast(1)
        col.addView(paragraph(ctx, "Цель на сегодня", 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                if (done >= goal) "Выполнено: $done из $goal. Отличная работа!"
                else "Пройдено $done из $goal квестов",
                15f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(bar(ctx, done.toFloat() / goal.toFloat()))
        col.addView(title(ctx, "ЧТО СЧИТАЕТСЯ КВЕСТОМ", 12))
        col.addView(paragraph(ctx, "Квест — это пять заданий по одному предмету.", 14f))
        col.addView(paragraph(ctx, "Задания открытого банка тоже идут в счёт цели.", 14f))
        col.addView(paragraph(ctx, "Дневная цель меняется в настройках.", 14f, R.color.on_bg_variant))
        col.addView(title(ctx, "СЕГОДНЯ", 14))
        col.addView(kv(ctx, "Квестов пройдено", done.toString()))
        col.addView(kv(ctx, "Дневная цель", goal.toString()))
        col.addView(kv(ctx, "Серия дней", "${prefs.streak}"))
        show(ctx, scroll(ctx, col))
    }

    /** Серия дней: правила и последние дни. */
    fun streak(ctx: Context, prefs: Prefs) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Серия: ${prefs.streak} дней", 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "Серия растёт, когда решаешь хотя бы один квест в день, и сбрасывается при пропуске дня.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(title(ctx, "ПОСЛЕДНИЕ ДНИ", 14))
        val days = prefs.activeDays.sorted().takeLast(7)
        if (days.isEmpty()) {
            col.addView(paragraph(ctx, "Пока ни одного дня с квестом.", 14f))
        } else {
            days.forEach { col.addView(kv(ctx, it, "квест пройден")) }
        }
        col.addView(title(ctx, "ИТОГИ", 14))
        col.addView(kv(ctx, "Текущая серия", "${prefs.streak}"))
        col.addView(kv(ctx, "Лучшая серия", "${prefs.bestDayStreak}"))
        col.addView(kv(ctx, "Дней с занятиями", "${prefs.activeDays.size}"))
        show(ctx, scroll(ctx, col))
    }

    /** Личный топ по предметам: где результат выше всего. */
    fun topSubjects(
        ctx: Context,
        best: Map<String, Int>,
        runs: Map<String, Int>,
        titleOf: (String) -> String
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Топ по предметам", 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                "Личный зачёт: лучший результат по каждому предмету.",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        val rows = best.entries.sortedByDescending { it.value }
        if (rows.isEmpty()) {
            col.addView(paragraph(ctx, "Пока нет пройденных квестов.", 14f, top = 10))
        }
        rows.forEachIndexed { i, e ->
            col.addView(title(ctx, "${i + 1}. ${titleOf(e.key)}", 12))
            col.addView(kv(ctx, "Лучший результат", "${e.value} очков"))
            col.addView(kv(ctx, "Пройдено квестов", "${runs[e.key] ?: 0}"))
        }
        show(ctx, scroll(ctx, col))
    }

    /** Список уведомлений: достижения, цель дня, серия, слабые темы. */
    fun notifications(ctx: Context, items: List<Notifier.Item>) {
        val col = column(ctx)
        col.addView(paragraph(ctx, "Уведомления", 22f, bold = true))
        if (items.isEmpty()) {
            col.addView(
                paragraph(
                    ctx,
                    "Пока новостей нет. Пройди квест — здесь появятся достижения и подсказки.",
                    15f,
                    R.color.on_bg_variant,
                    top = 6
                )
            )
        }
        items.forEach { item ->
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(ctx, 10), 0, dp(ctx, 10))
            val icon = ImageView(ctx)
            icon.setImageResource(item.iconRes)
            icon.setColorFilter(color(ctx, if (item.warn) R.color.danger else R.color.accent))
            val side = dp(ctx, 26)
            val iconBox = FrameLayout(ctx)
            iconBox.setBackgroundResource(R.drawable.bg_circle_surface)
            iconBox.addView(icon, FrameLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16), Gravity.CENTER))
            row.addView(
                iconBox,
                LinearLayout.LayoutParams(side, side).apply { marginEnd = dp(ctx, 12) }
            )
            val texts = LinearLayout(ctx)
            texts.orientation = LinearLayout.VERTICAL
            texts.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            texts.addView(
                TextView(ctx).apply {
                    text = item.title
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(color(ctx, R.color.on_bg))
                }
            )
            texts.addView(
                TextView(ctx).apply {
                    text = item.text
                    textSize = 13f
                    setTextColor(color(ctx, R.color.on_bg_variant))
                    setPadding(0, dp(ctx, 3), 0, 0)
                }
            )
            row.addView(texts)
            col.addView(row)
        }
        show(ctx, scroll(ctx, col))
    }

    // ------------------------------------------------------------------ достижения

    /** Достижение: как получить, получено ли и когда. */
    fun badge(ctx: Context, badge: Badge) {
        val col = column(ctx)
        val icon = ImageView(ctx).apply {
            setImageResource(BadgeIcons.of(badge.code))
            setColorFilter(color(ctx, R.color.accent))
        }
        val holder = FrameLayout(ctx)
        holder.setBackgroundResource(R.drawable.bg_circle_surface)
        val size = dp(ctx, 64)
        holder.addView(icon, FrameLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34), Gravity.CENTER))
        val wrap = LinearLayout(ctx)
        wrap.gravity = Gravity.CENTER_HORIZONTAL
        wrap.orientation = LinearLayout.VERTICAL
        wrap.addView(holder, LinearLayout.LayoutParams(size, size))
        col.addView(wrap)

        col.addView(
            paragraph(ctx, badge.title, 21f, bold = true).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(ctx, 12), 0, dp(ctx, 2))
            }
        )
        col.addView(
            paragraph(
                ctx,
                if (badge.isUnlocked) "Получено " + TimeFmt.short(badge.unlockedAt ?: 0L)
                else "Пока не получено",
                14f,
                if (badge.isUnlocked) R.color.secondary else R.color.on_bg_variant
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        col.addView(title(ctx, "КАК ПОЛУЧИТЬ", 18))
        col.addView(paragraph(ctx, badge.description, 15f))

        col.addView(title(ctx, "СТАТУС", 14))
        col.addView(kv(ctx, "Достижение", badge.title))
        col.addView(
            kv(
                ctx,
                "Состояние",
                if (badge.isUnlocked) "открыто" else "закрыто",
                if (badge.isUnlocked) R.color.secondary else R.color.on_bg_variant
            )
        )
        if (badge.isUnlocked) {
            col.addView(kv(ctx, "Дата", TimeFmt.short(badge.unlockedAt ?: 0L)))
        } else {
            col.addView(
                paragraph(
                    ctx,
                    "Продолжай решать квесты — достижение откроется автоматически.",
                    14f,
                    R.color.on_bg_variant,
                    top = 8
                )
            )
        }
        show(ctx, scroll(ctx, col))
    }

    // ------------------------------------------------------------------ история

    /** Открытая попытка: что решалось, что ответил игрок и что было верно. */
    fun attempt(
        ctx: Context,
        attempt: Attempt,
        rows: List<AnswerRow>,
        subjectTitle: String,
        onStats: (() -> Unit)? = null
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, subjectTitle, 22f, bold = true))
        col.addView(
            paragraph(
                ctx,
                TimeFmt.full(attempt.createdAt) + " · " + TimeFmt.duration(attempt.seconds),
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(title(ctx, "ИТОГ", 14))
        col.addView(kv(ctx, "Верных ответов", "${attempt.correct} из ${attempt.total}"))
        col.addView(kv(ctx, "Очки", "${attempt.score}"))
        col.addView(kv(ctx, "Оценка", Scoring.gradeLabel(attempt.grade)))
        col.addView(kv(ctx, "Время", TimeFmt.duration(attempt.seconds)))

        col.addView(title(ctx, "ЗАДАНИЯ", 16))
        if (rows.isEmpty()) {
            col.addView(
                paragraph(
                    ctx,
                    "Эта попытка сделана до обновления: задания в ней не сохранялись.",
                    14f,
                    R.color.on_bg_variant
                )
            )
        }
        rows.forEachIndexed { i, r ->
            val head = TextView(ctx).apply {
                text = "${i + 1}. " + if (r.isCorrect) "верно" else "неверно"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color(ctx, if (r.isCorrect) R.color.secondary else R.color.danger))
                setPadding(0, dp(ctx, 12), 0, dp(ctx, 2))
            }
            col.addView(head)
            if (r.topic.isNotBlank()) {
                col.addView(paragraph(ctx, r.topic, 13f, R.color.on_bg_variant))
            }
            if (r.question.isNotBlank()) {
                col.addView(paragraph(ctx, r.question, 14f))
            }
            col.addView(kv(ctx, "Твой ответ", r.typed.ifBlank { "нет ответа" }))
            col.addView(kv(ctx, "Верный ответ", r.expected.ifBlank { "—" }, R.color.accent))
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setView(scroll(ctx, col))
            .setPositiveButton("Закрыть", null)
        if (onStats != null) {
            builder.setNegativeButton("Статистика по предмету") { _, _ -> onStats() }
        }
        builder.show()
    }

    /**
     * Статистика по предмету: сколько раз проходили, как менялся результат
     * и какие темы проседают.
     */
    fun subjectStats(
        ctx: Context,
        subjectTitle: String,
        attempts: List<Attempt>,
        topics: List<TopicStat>,
        onAttempt: ((Attempt) -> Unit)? = null
    ) {
        val col = column(ctx)
        col.addView(paragraph(ctx, subjectTitle, 22f, bold = true))
        val best = attempts.maxOfOrNull { it.score } ?: 0
        val totalCorrect = attempts.sumOf { it.correct }
        val totalAnswers = attempts.sumOf { it.total }
        val accuracy = if (totalAnswers == 0) 0 else totalCorrect * 100 / totalAnswers
        col.addView(
            paragraph(
                ctx,
                "Попыток: ${attempts.size} · точность $accuracy%",
                14f,
                R.color.on_bg_variant,
                top = 4
            )
        )
        col.addView(title(ctx, "РЕЗУЛЬТАТЫ", 14))
        col.addView(kv(ctx, "Лучший результат", "$best очков"))
        col.addView(kv(ctx, "Верных ответов", "$totalCorrect из $totalAnswers"))
        col.addView(kv(ctx, "Оценка по последней", Scoring.gradeLabel(attempts.firstOrNull()?.grade ?: 0)))
        col.addView(title(ctx, "КАК МЕНЯЛСЯ РЕЗУЛЬТАТ", 14))
        attempts.take(8).reversed().forEach { a ->
            col.addView(bar(ctx, a.score.toFloat() / 50f))
            val line = paragraph(
                ctx,
                TimeFmt.short(a.createdAt) + " · ${a.score} очков · ${a.correct} из ${a.total}",
                13f,
                R.color.on_bg_variant
            )
            if (onAttempt != null) {
                // попытку можно раскрыть прямо из статистики предмета
                line.isClickable = true
                line.setOnClickListener { onAttempt(a) }
            }
            col.addView(line)
        }
        col.addView(title(ctx, "ТЕМЫ", 16))
        if (topics.isEmpty()) {
            col.addView(paragraph(ctx, "Пока нет данных по темам.", 14f, R.color.on_bg_variant))
        }
        topics.forEach { t ->
            col.addView(kv(ctx, t.topic, "${t.correct} из ${t.total}",
                if (t.accuracy >= 60) R.color.secondary else R.color.danger))
        }
        show(ctx, scroll(ctx, col))
    }

    /** Иллюстрация из библиотеки на весь экран. */
    fun image(ctx: Context, url: String, title: String) {
        val holder = LinearLayout(ctx)
        holder.orientation = LinearLayout.VERTICAL
        holder.setBackgroundColor(0xFF000000.toInt())
        holder.setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))
        val picture = ImageView(ctx)
        picture.adjustViewBounds = true
        picture.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.addView(
            picture,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f }
        )
        holder.addView(
            TextView(ctx).apply {
                text = title
                textSize = 14f
                setTextColor(0xFFEEEEEE.toInt())
                setPadding(0, dp(ctx, 10), 0, 0)
            }
        )
        ImageLoader.load(url, picture)
        MaterialAlertDialogBuilder(ctx)
            .setView(holder)
            .setPositiveButton("Закрыть", null)
            .show()
    }
}

/**
 * Уровни: считаются от набранных очков. Нужны и на главной, и в диалоге уровня.
 */
object Leveling {

    const val MAX = 6
    private val STEPS = intArrayOf(0, 100, 250, 450, 700, 1000, 1400)

    fun level(totalScore: Int): Int {
        var lvl = 1
        while (lvl < MAX && totalScore >= STEPS[lvl]) lvl++
        return lvl
    }

    fun startScore(level: Int): Int = STEPS[(level - 1).coerceIn(0, MAX - 1)]

    fun nextScore(level: Int): Int = STEPS[level.coerceIn(0, MAX)]

    fun title(level: Int): String = when (level) {
        1 -> "Новичок"
        2 -> "Ученик"
        3 -> "Практик"
        4 -> "Знаток"
        5 -> "Отличник"
        else -> "Мастер экзамена"
    }

    fun hint(level: Int): String = when (level) {
        1 -> "Первые шаги: реши несколько квестов."
        2 -> "Уже видно прогресс, продолжай в том же темпе."
        3 -> "Стабильный темп важнее рывков."
        4 -> "Половина пути до максимума."
        5 -> "Осталось совсем немного до высшего уровня."
        else -> "Высший уровень. Держи серию дней."
    }

    /** Доля прогресса внутри текущего уровня: 0..1. */
    fun progress(totalScore: Int): Float {
        val level = level(totalScore)
        if (level >= MAX) return 1f
        val cur = startScore(level)
        val next = nextScore(level)
        return ((totalScore - cur).toFloat() / (next - cur).toFloat()).coerceIn(0f, 1f)
    }
}
