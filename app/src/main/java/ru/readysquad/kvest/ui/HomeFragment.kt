package ru.readysquad.kvest.ui

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.databinding.FragmentHomeBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Avatars
import ru.readysquad.kvest.util.Streaks
import ru.readysquad.kvest.util.ThemeColors
import ru.readysquad.kvest.util.Greetings
import ru.readysquad.kvest.util.Notifier
import ru.readysquad.kvest.util.Leveling
import ru.readysquad.kvest.util.Dialogs
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

/** Главная: уровень, статистика, цель на день и быстрые действия. */
class HomeFragment : Fragment(R.layout.fragment_home), Refreshable {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }
    private val prefs by lazy { Prefs(requireContext()) }

    private val levels = listOf("Новичок", "Ученик", "Знаток", "Мастер", "Гуру", "Легенда")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnSettings.setOnClickListener { openSettings() }
        // профиль в шапке тоже открывает настройки
        b.avatar.setOnClickListener { openSettings() }
        b.hello.setOnClickListener { openSettings() }
        b.subhello.setOnClickListener { openSettings() }
        b.ctaCard.setOnClickListener { startNextQuest() }
        b.levelCard.setOnClickListener { showLevel() }
        b.streakCard.setOnClickListener { Dialogs.streak(requireContext(), prefs) }
        b.goalCard.setOnClickListener { Dialogs.goal(requireContext(), prefs, repo.stats()) }
        b.lastCard.setOnClickListener { showLastAttempt() }
        b.btnBell.setOnClickListener { showNotifications() }
        Anim.fadeInUp(b.ctaCard, 120)
        Anim.fadeInUp(b.levelCard, 60)
    }

    /** Что показать в уведомлениях: достижения, цель дня, серия, слабые темы. */
    private fun notifItems() = Notifier.build(prefs, repo.stats(), repo.badges(), repo.topicStats(null))

    private fun showNotifications() {
        Dialogs.notifications(requireContext(), notifItems())
        Notifier.markSeen(prefs, repo.badges())
        _b?.bellDot?.visibility = View.GONE
    }

    private fun showLevel() {
        Dialogs.level(requireContext(), prefs, repo.stats()) {
            Dialogs.topSubjects(
                requireContext(),
                repo.bestScores(),
                repo.questsBySubject()
            ) { code -> repo.subjects().firstOrNull { it.code == code }?.title ?: code }
        }
    }

    /** Последняя попытка: открываем её задания и ответы. */
    private fun showLastAttempt() {
        val last = repo.attempts(1).firstOrNull()
        if (last == null) {
            (activity as? MainActivity)?.openTab(R.id.tab_results)
            return
        }
        Dialogs.attempt(
            requireContext(),
            last,
            repo.attemptAnswers(last.id),
            last.subjectTitle
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun refresh() {
        val bind = _b ?: return
        val stats = repo.stats()

        // своя иконка профиля показывается вместо эмодзи, если она сохранена
        val custom = Avatars.load(requireContext(), prefs.avatarImage)
        if (custom != null) {
            val size = (52 * resources.displayMetrics.density).toInt()
            bind.avatar.text = ""
            bind.avatar.background = BitmapDrawable(resources, Avatars.circleBitmap(custom, size))
        } else {
            bind.avatar.text = prefs.avatar
            bind.avatar.setBackgroundResource(R.drawable.bg_circle_surface)
        }

        // приветствие в шапке: имя из профиля и род из настроек
        bind.hello.text = if (prefs.name.isBlank()) "Привет!" else "Привет, ${prefs.name}!"
        bind.subhello.text = when {
            stats.quests == 0 -> "${Greetings.ready(prefs.gender)} начать первый квест?"
            prefs.streak > 1 -> "Серия ${prefs.streak} дней — не разрывай её"
            stats.accuracy >= 80 -> "Точность ${stats.accuracy}% — отличный темп"
            else -> "${Greetings.ready(prefs.gender)} продолжать подготовку?"
        }

        val level = Leveling.level(stats.totalScore)
        bind.levelTitle.text = "Уровень $level · ${Leveling.title(level)}"
        bind.totalScore.text = "${stats.totalScore} очков"
        bind.levelBar.setProgress((Leveling.progress(stats.totalScore) * 100).toInt(), true)
        bind.levelHint.text = if (level >= Leveling.MAX) "Максимальный уровень"
        else "До уровня ${level + 1} осталось ${Leveling.nextScore(level) - stats.totalScore} очков"

        Anim.countUp(0, stats.quests, 700L) { bind.statQuests.text = it.toString() }
        bind.statAccuracy.text = "${stats.accuracy}%"
        bind.statMinutes.text = "${stats.minutes} мин"

        val goal = prefs.goalPerDay.coerceAtLeast(1)
        val today = prefs.todayQuests
        bind.goalText.text = "$today из $goal"
        bind.goalBar.setProgress((today * 100 / goal).coerceAtMost(100), true)

        bind.ctaTitle.text = if (stats.quests == 0) "Начать первый квест" else "Продолжить подготовку"
        val focus = prefs.focusTopics.firstOrNull()
        bind.ctaSubtitle.text = if (focus.isNullOrBlank()) "5 задач · ~4 минуты"
        else "Тема для прокачки: $focus"

        renderStreak(bind)
        renderLast(bind)
    }

    /** Серия дней подряд: огонёк, рекорд и полоска недели. */
    private fun renderStreak(bind: FragmentHomeBinding) {
        val broken = Streaks.isBroken(prefs)
        val streak = if (broken) 0 else prefs.streak

        bind.streakTitle.text = "Серия: $streak ${daysWord(streak)}"
        bind.streakHint.text = when {
            streak > 0 -> "Занимаешься каждый день — так держать"
            prefs.streak > 0 -> "Серия прервалась. Начни заново сегодня"
            else -> "Реши один квест сегодня, и серия начнётся"
        }
        bind.streakBest.text = "рекорд ${prefs.bestDayStreak}"
        bind.streakFlame.alpha = if (streak == 0) 0.4f else 1f

        val primary = ThemeColors.primary(requireContext())
        val onPrimary = ThemeColors.onPrimary(requireContext())
        val container = ThemeColors.surfaceContainer(requireContext())
        val inactive = ThemeColors.onSurfaceVariant(requireContext())

        bind.weekRow.removeAllViews()
        Streaks.week(prefs).forEach { day ->
            val cell = TextView(requireContext()).apply {
                text = day.label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (day.active) onPrimary else inactive)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (day.active) primary else container)
                    if (day.isToday && !day.active) setStroke(dp(2), primary)
                }
            }
            val lp = LinearLayout.LayoutParams(0, dp(34), 1f)
            lp.marginEnd = dp(4)
            cell.layoutParams = lp
            bind.weekRow.addView(cell)
        }
    }

    private fun daysWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "день"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "дня"
        else -> "дней"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderLast(bind: FragmentHomeBinding) {
        val last = repo.lastAttempt()
        if (last == null) {
            bind.lastCard.visibility = View.GONE
            return
        }
        bind.lastCard.visibility = View.VISIBLE
        bind.lastLine.text = "${last.subjectTitle} · ${last.correct} из ${last.total}"
        Icons.start(bind.lastLine, SubjectIcons.of(last.subjectCode), R.color.on_bg_variant, 13)
        bind.lastSub.text = "оценка ${last.grade} · ${last.score} очков · ${last.accuracy}% точности"
    }

    private fun startNextQuest() {
        val subjects = repo.subjects()
        if (subjects.isEmpty()) return
        val chosen = prefs.chosenSubjects
        val byFocus = prefs.focusTopics.firstOrNull()
            ?.let { topic -> subjects.firstOrNull { it.topics.contains(topic) } }
        val lastCode = repo.lastAttempt()?.subjectCode
        val subject = byFocus
            ?: subjects.firstOrNull { it.code == lastCode }
            ?: subjects.firstOrNull { chosen.contains(it.code) }
            ?: subjects.first()

        startActivity(
            Intent(requireContext(), QuizActivity::class.java)
                .putExtra(QuizActivity.EXTRA_SUBJECT, subject.code)
                .putExtra(QuizActivity.EXTRA_TITLE, subject.title)
                .putExtra(QuizActivity.EXTRA_EMOJI, subject.emoji)
        )
    }

    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
