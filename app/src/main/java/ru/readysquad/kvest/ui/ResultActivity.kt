package ru.readysquad.kvest.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.QuestResult
import ru.readysquad.kvest.data.Scoring
import ru.readysquad.kvest.databinding.ActivityResultBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Share
import ru.readysquad.kvest.util.BadgeIcons
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

/** Экран результатов: прогноз оценки, очки, бейджи, слабые темы и шаринг. */
class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESULT = "quest_result"

        /** Куда ведёт кнопка «Ещё квест»: обычные задания, реальные задания или бланк. */
        const val EXTRA_NEXT = "next_action"
        const val NEXT_QUIZ = "quiz"
        const val NEXT_REAL = "real"
    }

    private lateinit var b: ActivityResultBinding
    private lateinit var result: QuestResult

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // применяем выбранную палитру до создания разметки
        Palettes.apply(this, Prefs(this).palette)
        b = ActivityResultBinding.inflate(layoutInflater)
        setContentView(b.root)

        val extra = intent.getSerializableExtra(EXTRA_RESULT)
        if (extra !is QuestResult) {
            finish()
            return
        }
        result = extra

        bind()
        animate()
    }

    private fun bind() {
        b.resultSubject.text = "${result.subjectTitle.uppercase()} · КВЕСТ ПРОЙДЕН"
        Icons.start(b.resultSubject, SubjectIcons.of(result.subjectCode), R.color.on_bg, 16)
        b.gradeNumber.text = result.grade.toString()
        b.gradeLabel.text = Scoring.gradeLabel(result.grade)
        b.gradeComment.text = Scoring.gradeComment(result.grade)

        b.statCorrect.text = "${result.correct}/${result.total}"
        b.statTime.text = "${result.seconds}с"
        b.statStreak.text = result.bestStreak.toString()

        // из чего сложились очки
        b.bonusBox.removeAllViews()
        addBonusRow("Базовые очки", "+${result.baseScore}", false)
        addBonusRow("Бонус за скорость", if (result.speedBonus > 0) "+${result.speedBonus}" else "0", result.speedBonus > 0)
        addBonusRow("Бонус за серию", if (result.streakBonus > 0) "+${result.streakBonus}" else "0", result.streakBonus > 0)

        // новые бейджи
        if (result.newBadges.isEmpty()) {
            b.newBadgesBox.visibility = View.GONE
        } else {
            b.newBadgesBox.visibility = View.VISIBLE
            b.newBadgesBox.removeAllViews()
            val header = TextView(this).apply {
                text = "Новые достижения: ${result.newBadges.size}"
                setTextColor(ContextCompat.getColor(this@ResultActivity, R.color.on_bg))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            b.newBadgesBox.addView(header)
            result.newBadges.forEachIndexed { i, badge ->
                val row = TextView(this).apply {
                    text = "${badge.title} — ${badge.description}"
                    setTextColor(ContextCompat.getColor(this@ResultActivity, R.color.on_bg))
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = ContextCompat.getDrawable(this@ResultActivity, R.drawable.bg_stat)
                    Icons.start(this, BadgeIcons.of(badge.code), R.color.on_bg_variant, 16)
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(8)
                row.layoutParams = lp
                b.newBadgesBox.addView(row)
                Anim.pop(row, 120L * i)
            }
        }

        // слабые темы
        if (result.topicsToImprove.isEmpty()) {
            b.topicsCard.visibility = View.GONE
        } else {
            b.topicsCard.visibility = View.VISIBLE
            b.topicsBox.removeAllViews()
            result.topicsToImprove.forEach { t ->
                val row = TextView(this).apply {
                    text = "• ${t.topic}: ${t.correct} из ${t.total} — стоит повторить"
                    setTextColor(ContextCompat.getColor(this@ResultActivity, R.color.on_bg_variant))
                    textSize = 14f
                    setPadding(0, dp(5), 0, dp(5))
                }
                b.topicsBox.addView(row)
            }
        }

        // шаринг
        val body = Share.text(result.score, result.subjectTitle, result.correct, result.total, result.grade)
        b.btnVk.setOnClickListener { Share.toVk(this, body) }
        b.btnTg.setOnClickListener { Share.toTelegram(this, body) }
        b.btnMoreShare.setOnClickListener { Share.generic(this, body) }

        b.btnAgain.setOnClickListener {
            val next = if (intent.getStringExtra(EXTRA_NEXT) == NEXT_REAL) {
                Intent(this, RealTasksActivity::class.java)
                    .putExtra(RealTasksActivity.EXTRA_SUBJECT, result.subjectCode)
            } else {
                Intent(this, QuizActivity::class.java)
                    .putExtra(QuizActivity.EXTRA_SUBJECT, result.subjectCode)
                    .putExtra(QuizActivity.EXTRA_TITLE, result.subjectTitle)
                    .putExtra(QuizActivity.EXTRA_EMOJI, result.subjectEmoji)
            }
            startActivity(next)
            finish()
        }
        b.btnHome.setOnClickListener { finish() }
    }

    private fun addBonusRow(label: String, value: String, highlight: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val left = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@ResultActivity, R.color.on_bg))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val right = TextView(this).apply {
            text = value
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(
                ContextCompat.getColor(
                    this@ResultActivity,
                    if (highlight) R.color.accent else R.color.on_bg_variant
                )
            )
        }
        row.addView(left)
        row.addView(right)
        b.bonusBox.addView(row)
    }

    private fun animate() {
        b.gradeCircle.scaleX = 0.5f
        b.gradeCircle.scaleY = 0.5f
        b.gradeCircle.alpha = 0f
        b.gradeCircle.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(560).start()

        Anim.countUp(0, result.score, 1000L) { b.scoreBig.text = "$it очков" }
        Anim.fadeInUp(b.bonusBox, 260)
        Anim.fadeInUp(b.gradeComment, 360)

        b.confetti.post {
            if (result.correct >= result.total / 2) b.confetti.burst()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
