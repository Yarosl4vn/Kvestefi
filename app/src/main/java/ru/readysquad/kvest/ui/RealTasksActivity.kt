package ru.readysquad.kvest.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.AnswerNorm
import ru.readysquad.kvest.data.AnswerRow
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.BadgeEngine
import ru.readysquad.kvest.data.Exam
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.QuestEvent
import ru.readysquad.kvest.data.QuestResult
import ru.readysquad.kvest.data.RealTask
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Sdamgia
import ru.readysquad.kvest.data.Scoring
import ru.readysquad.kvest.data.Subject
import ru.readysquad.kvest.data.TopicStat
import ru.readysquad.kvest.databinding.ActivityRealTasksBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.ImageLoader
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.util.Streaks
import java.util.Calendar
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

/**
 * Квест по реальным заданиям открытого банка: настоящая формулировка, рисунок,
 * ответ в клетки бланка ответов № 1 и разбор после проверки.
 *
 * Задания запрашиваются в интернете в момент старта квеста и не сохраняются,
 * поэтому приложение не хранит чужой банк заданий у себя.
 *
 * Очки считаются как в остальных квестах: 10 за верный ответ, +5 за скорость
 * и +5 за продолжение серии из трёх и более верных ответов подряд.
 */
class RealTasksActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SUBJECT = "subject_code"
        private const val QUEST_SIZE = 5
        private const val MIN_CELLS = 2
        private const val MAX_CELLS = 12
        private const val SPEED_LIMIT_SEC = 45
    }

    private lateinit var b: ActivityRealTasksBinding
    private val repo by lazy { Repository.get(this) }
    private val prefs by lazy { Prefs(this) }

    private var subject: Subject? = null
    private var exam: String = Exam.OGE
    private var tasks: List<RealTask> = emptyList()

    /** true — показываем выбор предмета, тест ещё не начат. */
    private var picking = false

    /** Номер последнего запроса: ответы устаревших запросов игнорируем. */
    private var requestId = 0

    private var index = 0
    private var correctCount = 0
    private var questSeconds = 0
    private var taskSeconds = 0
    private var runStreak = 0
    private var questBestStreak = 0
    private var speedBonus = 0
    private var streakBonus = 0
    private var fastAnswers = 0

    private var answered = false
    private var finishing = false
    private var retryPending = false
    private val cells = ArrayList<EditText>()
    private val rows = ArrayList<AnswerRow>()

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (answered || finishing) return
            taskSeconds++
            renderTimer()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // палитра применяется до создания разметки
        Palettes.apply(this, Prefs(this).palette)
        b = ActivityRealTasksBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }
        b.btnSkip.setOnClickListener { reveal(byUser = true) }
        b.btnCheck.setOnClickListener { onPrimaryAction() }
        b.btnStart.setOnClickListener {
            picking = false
            b.pickerBox.visibility = View.GONE
            loadQuest()
        }

        val requested = intent.getStringExtra(EXTRA_SUBJECT)
        subject = availableSubjects().firstOrNull { it.code == requested }
            ?: availableSubjects().firstOrNull()
        buildSubjectChips()
        buildExamChips()

        // Заходим из списка предметов — сразу тест. Открыли раздел сами —
        // сначала спрашиваем, какой предмет и уровень нужны.
        if (requested != null) {
            picking = false
            b.pickerBox.visibility = View.GONE
            loadQuest()
        } else {
            showPicker()
        }
    }

    /** Экран выбора: предмет, уровень и кнопка старта. */
    private fun showPicker() {
        picking = true
        handler.removeCallbacks(ticker)
        b.pickerBox.visibility = View.VISIBLE
        b.loadingBox.visibility = View.GONE
        b.questScroll.visibility = View.GONE
        b.progressText.visibility = View.GONE
        b.counterText.visibility = View.GONE
        b.btnSkip.visibility = View.GONE
        b.btnCheck.visibility = View.GONE
        b.taskCard.visibility = View.GONE
        b.resultCard.visibility = View.GONE
        b.finalCard.visibility = View.GONE
        val s = subject
        b.rtSubtitle.text = "Открытый банк заданий"
        b.pickerHint.text = if (s == null) {
            "Для этого раздела нет онлайн-источника."
        } else {
            "${s.title} · ${exam}. Задания настоящие: рисунок, разбор и ссылка на источник. " +
                "Ответ вписывается в клетки, как в бланке ответов."
        }
        b.btnStart.isEnabled = s != null
    }

    /** Предметы, для которых подключён онлайн-источник заданий. */
    private fun availableSubjects(): List<Subject> =
        repo.subjects().filter { Sdamgia.hasSource(it.code) }

    // ------------------------------------------------------------------ загрузка

    private fun loadQuest() {
        val s = subject ?: run {
            showMessage("Заданий нет", "В приложении нет предметов с онлайн-источником.")
            return
        }
        requestId++
        val current = requestId
        retryPending = false
        picking = false
        b.pickerBox.visibility = View.GONE
        b.rtSubtitle.text = "${s.title} · открытый банк заданий"
        showLoading(true)
        b.progressText.text = "Загружаем задания…"
        b.counterText.text = ""

        Thread {
            val result = Sdamgia.quest(s.code, exam, QUEST_SIZE, seed = System.nanoTime() + current)
            handler.post { if (current == requestId) onLoaded(result) }
        }.start()
    }

    private fun onLoaded(result: Sdamgia.Result) {
        showLoading(false)
        if (result.tasks.isEmpty()) {
            val reason = result.error ?: "Не удалось получить задания"
            retryPending = true
            showMessage("Не получилось загрузить задания", "$reason\n\nКвест по реальным заданиям работает онлайн.")
            b.btnCheck.visibility = View.VISIBLE
            b.btnCheck.text = "Повторить"
            b.btnSkip.visibility = View.GONE
            return
        }

        // если по нужному уровню заданий не нашлось, источник отдал второй уровень:
        // показываем это в переключателе, чтобы не было расхождений
        val usedExam = result.tasks.firstOrNull()?.exam
        if (usedExam != null && usedExam != exam) {
            exam = usedExam
            buildExamChips()
        }
        tasks = result.tasks.map { t ->
            RealTask(
                id = t.id,
                subjectCode = t.subjectCode,
                exam = t.exam,
                topic = t.topic,
                number = "",
                condition = t.question,
                answer = t.answer,
                solution = t.explanation,
                image = t.imageUrl,
                url = t.source
            )
        }
        index = 0
        correctCount = 0
        questSeconds = 0
        questBestStreak = 0
        speedBonus = 0
        streakBonus = 0
        fastAnswers = 0
        runStreak = 0
        finishing = false
        rows.clear()
        b.finalCard.visibility = View.GONE
        b.taskCard.visibility = View.VISIBLE
        b.btnSkip.visibility = View.VISIBLE
        b.btnCheck.visibility = View.VISIBLE
        showTask()
    }

    private fun showLoading(loading: Boolean) {
        b.loadingBox.visibility = if (loading) View.VISIBLE else View.GONE
        b.questScroll.visibility = if (loading) View.GONE else View.VISIBLE
        if (loading) {
            b.btnSkip.visibility = View.GONE
            b.btnCheck.visibility = View.GONE
            b.taskCard.visibility = View.GONE
            b.resultCard.visibility = View.GONE
            b.finalCard.visibility = View.GONE
        }
    }

    private fun showMessage(title: String, message: String) {
        handler.removeCallbacks(ticker)
        b.loadingBox.visibility = View.GONE
        b.questScroll.visibility = View.VISIBLE
        b.finalCard.visibility = View.VISIBLE
        b.finalTitle.text = title
        b.finalBody.text = message
        b.taskCard.visibility = View.GONE
        b.resultCard.visibility = View.GONE
        b.progressText.visibility = View.GONE
        b.counterText.visibility = View.GONE
    }

    // ------------------------------------------------------------------ переключатели

    private fun buildSubjectChips() {
        b.subjectRow.removeAllViews()
        availableSubjects().forEach { s ->
            val chip = TextView(this).apply {
                text = s.title
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = ContextCompat.getDrawable(this@RealTasksActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@RealTasksActivity, R.color.chip_text))
                isSelected = s.code == subject?.code
                isClickable = true
                isFocusable = true
                Icons.start(this, SubjectIcons.of(s.code), R.color.chip_text, 15)
                setOnClickListener {
                    if (s.code == subject?.code) return@setOnClickListener
                    subject = s
                    buildSubjectChips()
                    // на экране выбора предмет только отмечаем, тест начнётся по кнопке
                    if (picking) showPicker() else loadQuest()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            chip.layoutParams = lp
            b.subjectRow.addView(chip)
        }
    }

    /** Уровень экзамена: ОГЭ или ЕГЭ. */
    private fun buildExamChips() {
        b.examRow.removeAllViews()
        listOf(Exam.OGE, Exam.EGE).forEach { e ->
            val chip = TextView(this).apply {
                text = e
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(7), dp(16), dp(7))
                background = ContextCompat.getDrawable(this@RealTasksActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@RealTasksActivity, R.color.chip_text))
                isSelected = e == exam
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (e == exam) return@setOnClickListener
                    exam = e
                    buildExamChips()
                    if (picking) showPicker() else loadQuest()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            chip.layoutParams = lp
            b.examRow.addView(chip)
        }
    }

    // ------------------------------------------------------------------ задание

    private fun showTask() {
        val task = tasks.getOrNull(index) ?: return
        answered = false
        taskSeconds = 0
        cells.clear()

        b.progressText.visibility = View.VISIBLE
        b.counterText.visibility = View.VISIBLE
        b.progressText.text = "Задание ${index + 1} из ${tasks.size}"
        b.counterText.text = "верно $correctCount"

        b.taskCard.visibility = View.VISIBLE
        b.taskTopic.text = task.topic
        b.taskExam.text = task.exam
        b.taskText.text = task.condition

        // рисунок грузим из сети; если не получилось, рамку убираем
        val image = task.image
        if (!image.isNullOrBlank()) {
            b.taskImage.visibility = View.VISIBLE
            ImageLoader.load(image, b.taskImage, referer = task.url) {
                b.taskImage.visibility = View.GONE
            }
        } else {
            b.taskImage.setImageDrawable(null)
            b.taskImage.visibility = View.GONE
        }

        if (task.url.isNotBlank()) {
            b.taskSource.text = "Источник: ${task.url}"
            b.taskSource.visibility = View.VISIBLE
        } else {
            b.taskSource.visibility = View.GONE
        }

        // рисунок следующего задания готовим заранее, пока решается текущее
        tasks.getOrNull(index + 1)?.let { next -> ImageLoader.prefetch(next.image, next.url) }

        buildCells(task)

        b.resultCard.visibility = View.GONE
        b.btnSkip.visibility = View.VISIBLE
        b.btnSkip.isEnabled = true
        b.btnCheck.visibility = View.VISIBLE
        b.btnCheck.text = "Проверить"
        renderTimer()

        Anim.fadeInUp(b.taskCard, 0, 14f)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000L)
        b.questScroll.post { b.questScroll.smoothScrollTo(0, 0) }
    }

    /** Клетки как в бланке: одна цифра или буква на клетку, ввод идёт вперёд. */
    private fun buildCells(task: RealTask) {
        Icons.start(b.timerText, R.drawable.ic_card_timer, R.color.on_bg_variant, 13)
        b.cellBox.removeAllViews()
        val count = AnswerNorm.normalize(task.answer).length.coerceIn(MIN_CELLS, MAX_CELLS)
        repeat(count) { i ->
            val cell = newCell()
            b.cellBox.addView(cell, cellLp())
            cells.add(cell)
        }
        cells.forEachIndexed { i, cell ->
            cell.addTextChangedListener(CellWatcher(cells, i))
            cell.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    val current = v as EditText
                    if (current.text.isEmpty() && i > 0) {
                        cells[i - 1].setText("")
                        cells[i - 1].requestFocus()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }
        cells.firstOrNull()?.post { cells.lastOrNull()?.clearFocus() }
    }

    private fun newCell(): EditText = EditText(this).apply {
        gravity = Gravity.CENTER
        textSize = 15f
        isSingleLine = true
        setPadding(0, 0, 0, 0)
        background = ContextCompat.getDrawable(this@RealTasksActivity, R.drawable.bg_cell)
        filters = arrayOf<InputFilter>(InputFilter.LengthFilter(1))
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_NEXT
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        setTextColor(ContextCompat.getColor(this@RealTasksActivity, R.color.on_bg))
    }

    private fun cellLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(28), dp(40)).apply { marginEnd = dp(4) }

    private inner class CellWatcher(
        private val list: List<EditText>,
        private val position: Int
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (s.isNullOrEmpty()) return
            if (position + 1 < list.size) list[position + 1].requestFocus() else list[position].clearFocus()
        }
    }

    private fun renderTimer() {
        val left = SPEED_LIMIT_SEC - taskSeconds
        b.timerText.text = if (left > 0) {
            "$taskSeconds сек · решишь быстрее $SPEED_LIMIT_SEC секунд — бонус +${Scoring.SPEED_BONUS}"
        } else {
            "$taskSeconds сек · бонус за скорость уже не успеть, решай спокойно"
        }
    }

    // ------------------------------------------------------------------ проверка

    private fun onPrimaryAction() {
        if (retryPending) {
            loadQuest()
            return
        }
        if (finishing) {
            finish()
            return
        }
        if (answered) {
            next()
            return
        }
        checkAnswer()
    }

    private fun typedAnswer(): String = cells.joinToString("") { it.text.toString() }

    private fun checkAnswer() {
        val task = tasks.getOrNull(index) ?: return
        val typed = typedAnswer()
        if (typed.isBlank()) {
            Toast.makeText(this, "Впиши ответ в клетки бланка", Toast.LENGTH_SHORT).show()
            return
        }

        val ok = AnswerNorm.matchesSequence(typed, task.answer)
        if (ok) {
            correctCount++
            runStreak++
            if (runStreak > questBestStreak) questBestStreak = runStreak
            if (taskSeconds <= SPEED_LIMIT_SEC) {
                speedBonus += Scoring.SPEED_BONUS
                fastAnswers++
            }
            if (runStreak >= Scoring.STREAK_MIN) streakBonus += Scoring.STREAK_BONUS
        } else {
            runStreak = 0
        }
        questSeconds += taskSeconds

        rows.add(
            AnswerRow(
                taskId = task.id,
                topic = task.topic,
                isCorrect = ok,
                seconds = taskSeconds,
                question = task.condition,
                typed = typed,
                expected = task.answer
            )
        )
        b.counterText.text = "верно $correctCount"

        cells.forEach { cell ->
            cell.setBackgroundResource(if (ok) R.drawable.bg_cell_ok else R.drawable.bg_cell_wrong)
            cell.isEnabled = false
        }

        val earned = if (ok) {
            Scoring.POINTS_PER_TASK + (if (taskSeconds <= SPEED_LIMIT_SEC) Scoring.SPEED_BONUS else 0)
        } else {
            0
        }
        b.resultVerdict.setTextColor(
            ContextCompat.getColor(this, if (ok) R.color.success else R.color.danger)
        )
        b.resultVerdict.text = when {
            ok && taskSeconds <= SPEED_LIMIT_SEC -> "Верно! +$earned очков, с бонусом за скорость"
            ok -> "Верно! +$earned очков"
            else -> "Неверно"
        }
        b.resultExpected.text = if (ok) {
            "Ты вписал: $typed"
        } else {
            "Ты вписал: «${typed.ifBlank { "пусто" }}». Правильный ответ: ${task.answer}"
        }
        b.resultSolution.text = if (task.solution.isNotBlank()) {
            "Разбор: ${task.solution}"
        } else {
            "Разбор этого задания открыт по ссылке на источник выше."
        }
        showResultCard()
    }

    /** Пользователь попросил показать ответ: разбираем без начисления очков. */
    private fun reveal(byUser: Boolean) {
        if (answered) return
        val task = tasks.getOrNull(index) ?: return
        questSeconds += taskSeconds
        runStreak = 0
        rows.add(
            AnswerRow(
                taskId = task.id,
                topic = task.topic,
                isCorrect = false,
                seconds = taskSeconds,
                question = task.condition,
                typed = typedAnswer(),
                expected = task.answer
            )
        )

        cells.forEach { cell ->
            cell.setBackgroundResource(R.drawable.bg_cell_wrong)
            cell.isEnabled = false
        }
        b.resultVerdict.setTextColor(ContextCompat.getColor(this, R.color.on_bg))
        b.resultVerdict.text = if (byUser) "Смотрим правильный ответ" else "Время вышло"
        b.resultExpected.text = "Ты вписал: «${typedAnswer().ifBlank { "пусто" }}». " +
            "Правильный ответ: ${task.answer}"
        b.resultSolution.text = if (task.solution.isNotBlank()) {
            "Разбор: ${task.solution}"
        } else {
            "Разбор этого задания открыт по ссылке на источник выше."
        }
        showResultCard()
    }

    private fun showResultCard() {
        answered = true
        handler.removeCallbacks(ticker)
        b.resultCard.visibility = View.VISIBLE
        Anim.fadeInUp(b.resultCard, 0, 14f)
        b.btnSkip.visibility = View.GONE
        b.btnCheck.text = if (index + 1 < tasks.size) "Следующее задание" else "Итоги квеста"
        b.questScroll.post { b.questScroll.smoothScrollTo(0, b.resultCard.top) }
    }

    private fun next() {
        index++
        if (index < tasks.size) showTask() else finishQuest()
    }

    // ------------------------------------------------------------------ итоги

    private fun finishQuest() {
        finishing = true
        handler.removeCallbacks(ticker)
        val s = subject ?: return
        val total = tasks.size
        val baseScore = correctCount * Scoring.POINTS_PER_TASK
        val score = baseScore + speedBonus + streakBonus
        val accuracy = if (total == 0) 0 else correctCount * 100 / total
        val grade = Scoring.grade(accuracy)

        repo.saveAttempt(
            subjectCode = s.code,
            correct = correctCount,
            total = total,
            score = score,
            grade = grade,
            seconds = questSeconds,
            answers = rows
        )

        // решение заданий продлевает серию дней
        val streakDays = Streaks.touch(prefs)
        if (streakDays > prefs.bestDayStreak) prefs.bestDayStreak = streakDays

        val stats = repo.stats()
        val event = QuestEvent(
            correct = correctCount,
            total = total,
            fastAnswers = fastAnswers,
            grade = grade,
            questsCount = stats.quests,
            totalScore = stats.totalScore,
            bestStreak = maxOf(prefs.bestStreak, questBestStreak),
            distinctSubjects = stats.distinctSubjects,
            streakDays = streakDays,
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )
        val newBadges: List<Badge> = BadgeEngine.evaluate(repo, event)
        val weak: List<TopicStat> = repo.topicStats(s.code)
            .filter { it.total > 0 && it.accuracy < 60 }
            .take(3)

        startActivity(
            Intent(this, ResultActivity::class.java)
                .putExtra(
                    ResultActivity.EXTRA_RESULT,
                    QuestResult(
                        subjectCode = s.code,
                        subjectTitle = s.title,
                        subjectEmoji = s.emoji,
                        correct = correctCount,
                        total = total,
                        score = score,
                        baseScore = baseScore,
                        speedBonus = speedBonus,
                        streakBonus = streakBonus,
                        grade = grade,
                        seconds = questSeconds,
                        bestStreak = questBestStreak,
                        newBadges = newBadges,
                        topicsToImprove = weak
                    )
                )
                .putExtra(ResultActivity.EXTRA_NEXT, ResultActivity.NEXT_REAL)
        )
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        requestId++
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
