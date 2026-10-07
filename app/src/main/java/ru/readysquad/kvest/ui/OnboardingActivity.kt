package ru.readysquad.kvest.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.SeedData
import ru.readysquad.kvest.data.Subject
import ru.readysquad.kvest.databinding.ActivityOnboardingBinding
import ru.readysquad.kvest.databinding.ItemOnboardSubjectBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons
import ru.readysquad.kvest.util.Greetings

/**
 * Первый запуск: приветствие и быстрая настройка —
 * имя, аватар, предметы, слабые темы, цель на день и тема оформления.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding
    private val prefs by lazy { Prefs(this) }
    private val repo by lazy { Repository.get(this) }

    private val steps: List<View> get() = listOf(b.stepWelcome, b.stepName, b.stepSubjects, b.stepGoal)
    private val goalLabels = listOf(1 to "1 квест", 3 to "3 квеста", 5 to "5 квестов")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // применяем выбранную палитру до создания разметки
        Palettes.apply(this, Prefs(this).palette)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        fillFeatures()
        buildAvatars()
        buildGenders()
        buildSubjects()
        buildTopics()
        buildGoals()
        buildDots()

        b.themeSwitch.isChecked = prefs.themeMode == AppCompatDelegate.MODE_NIGHT_YES
        b.hintsSwitch.isChecked = prefs.soundHints

        // восстановление после пересоздания (например, при смене темы)
        // по умолчанию отмечаем три основных предмета ОГЭ, остальные — по желанию,
        // иначе список тем на шаге 3 получается слишком длинным
        if (OnboardingDraft.subjects.isEmpty()) {
            OnboardingDraft.subjects.addAll(repo.subjects().take(3).map { it.code })
        }
        b.nameInput.setText(OnboardingDraft.name)
        syncSelections()
        showStep(OnboardingDraft.step.coerceIn(0, 3), animate = false)

        b.btnNext.setOnClickListener { onNext() }
        b.btnBack.setOnClickListener {
            if (OnboardingDraft.step > 0) showStep(OnboardingDraft.step - 1, animate = true) else Anim.shake(b.logoBubble)
        }
        b.skip.setOnClickListener { finishOnboarding() }

        b.themeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.saveThemeMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            // применяем сразу — пользователь видит результат; состояние шага сохраняется в черновике
            AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        }
        b.hintsSwitch.setOnCheckedChangeListener { _, checked -> prefs.soundHints = checked }

        Anim.pop(b.logoBubble)
        Anim.fadeInUp(b.stepHost, 80)
    }

    // ------------------------------------------------------------------ наполнение

    private fun fillFeatures() {
        val data = listOf(
            Triple(R.drawable.ic_badge_bolt, "Очки за скорость", "Ответил быстрее 10 секунд — плюс 5 бонусных очков."),
            Triple(R.drawable.ic_card_chart, "Прогноз оценки", "Результат оценивается так же, как на реальном экзамене."),
            Triple(R.drawable.ic_badge_target, "Реальные задания", "Задания открытого банка с рисунками, разбором и ссылкой на источник.")
        )
        val rows = listOf(b.feature1, b.feature2, b.feature3)
        rows.forEachIndexed { i, row ->
            val (icon, title, text) = data[i]
            row.featureIcon.setImageResource(icon)
            row.featureTitle.text = title
            row.featureText.text = text
            Anim.fadeInUp(row.root, 160L + i * 90L, 26f)
        }
    }

    private fun buildAvatars() {
        b.avatarGrid.removeAllViews()
        val size = dp(58)
        SeedData.avatarChoices.forEach { emoji ->
            val tv = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.bg_avatar_choice)
                isClickable = true
                isFocusable = true
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            // квадратная ячейка: растянутая по колонке была овалом
            lp.width = size
            lp.height = size
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            tv.layoutParams = lp
            tv.setOnClickListener {
                OnboardingDraft.avatar = emoji
                syncSelections()
                Anim.pop(tv, 0)
            }
            b.avatarGrid.addView(tv)
        }
    }

    /** Пол: влияет только на род в приветствии и подписях. */
    private fun buildGenders() {
        b.genderRow.removeAllViews()
        val options = listOf(
            Greetings.MALE to ("Мужской" to R.drawable.ic_card_male),
            Greetings.FEMALE to ("Женский" to R.drawable.ic_card_female)
        )
        options.forEach { (value, data) ->
            val (label, icon) = data
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(13), dp(10), dp(13))
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@OnboardingActivity, R.color.chip_text))
                isSelected = OnboardingDraft.gender == value
                isClickable = true
                isFocusable = true
                Icons.start(this, icon, R.color.chip_text, 15)
                setOnClickListener {
                    OnboardingDraft.gender = value
                    syncSelections()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.genderRow.addView(chip)
        }
    }

    private fun buildSubjects() {
        b.subjectBox.removeAllViews()
        repo.subjects().forEach { subject ->
            val row = ItemOnboardSubjectBinding.inflate(layoutInflater, b.subjectBox, false)
            row.onboardSubjectIcon.setImageResource(SubjectIcons.of(subject.code))
            row.onboardSubjectTitle.text = subject.title
            row.onboardSubjectTopics.text = subject.topics.replace(", ", " · ")
            row.root.setOnClickListener {
                OnboardingDraft.subjects.toggleItem(subject.code)
                // список тем зависит от выбранных предметов
                buildTopics()
                syncSelections()
                Anim.release(row.root)
            }
            b.subjectBox.addView(row.root)
            Anim.fadeInUp(row.root, 120)
        }
    }

    /**
     * Слабые темы идут подкатегориями: у каждого выбранного предмета свой блок
     * с заголовком и кнопкой «все темы». Так видно, к какому предмету относится тема.
     */
    private fun buildTopics() {
        val bind = b
        bind.topicBox.removeAllViews()
        val codes = if (OnboardingDraft.subjects.isEmpty()) null else OnboardingDraft.subjects
        val subjects = repo.subjects().filter { codes == null || codes.contains(it.code) }
        if (subjects.isEmpty()) return

        subjects.forEach { subject ->
            val topics = SeedData.topicsBySubject[subject.code].orEmpty()
            if (topics.isEmpty()) return@forEach
            bind.topicBox.addView(groupHeader(subject, topics))
            bind.topicBox.addView(groupGrid(topics))
            Anim.fadeInUp(bind.topicBox, 80)
        }
    }

    /** Заголовок подкатегории: предмет и кнопка «все темы». */
    private fun groupHeader(subject: Subject, topics: List<String>): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(14), 0, dp(6))
        }
        header.addView(
            TextView(this).apply {
                text = subject.title
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.on_bg))
                Icons.start(this, SubjectIcons.of(subject.code), R.color.on_bg, 15)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(TextView(this).apply {
            text = "все темы"
            textSize = 12f
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.bg_pill)
            setTextColor(ContextCompat.getColor(this@OnboardingActivity, R.color.accent))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // если отмечены уже все темы предмета — снимаем отметки
                if (topics.all { OnboardingDraft.topics.contains(it) }) {
                    topics.forEach { OnboardingDraft.topics.remove(it) }
                } else {
                    topics.forEach { OnboardingDraft.topics.add(it) }
                }
                syncSelections()
                Anim.pop(this, 0)
            }
        })
        return header
    }

    /** Темы одного предмета — в две колонки. */
    private fun groupGrid(topics: List<String>): GridLayout {
        val grid = GridLayout(this).apply { columnCount = 2 }
        topics.forEach { topic ->
            val chip = TextView(this).apply {
                text = topic
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.bg_topic_choice)
                setTextColor(ContextCompat.getColorStateList(this@OnboardingActivity, R.color.chip_text))
                isClickable = true
                isFocusable = true
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            chip.layoutParams = lp
            chip.setOnClickListener {
                OnboardingDraft.topics.toggleItem(topic)
                syncSelections()
                Anim.pop(chip, 0)
            }
            grid.addView(chip)
        }
        return grid
    }

    private fun buildGoals() {
        b.goalRow.removeAllViews()
        goalLabels.forEach { (value, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(14), dp(6), dp(14))
                background = ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@OnboardingActivity, R.color.chip_text))
                isClickable = true
                isFocusable = true
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            chip.setOnClickListener {
                OnboardingDraft.goal = value
                syncSelections()
                Anim.pop(chip, 0)
            }
            b.goalRow.addView(chip)
        }
    }

    private fun buildDots() {
        b.dots.removeAllViews()
        repeat(4) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(dp(8), dp(8))
            lp.setMargins(dp(4), 0, dp(4), 0)
            dot.layoutParams = lp
            b.dots.addView(dot)
        }
    }

    // ------------------------------------------------------------------ состояние

    /** Приводит выделения всех шагов в соответствие с черновиком. */
    private fun syncSelections() {
        for (i in 0 until b.avatarGrid.childCount) {
            val v = b.avatarGrid.getChildAt(i) as TextView
            v.isSelected = v.text.toString() == OnboardingDraft.avatar
        }
        for (i in 0 until b.subjectBox.childCount) {
            val row = b.subjectBox.getChildAt(i)
            val check = row.findViewById<TextView>(R.id.onboardSubjectCheck)
            val title = row.findViewById<TextView>(R.id.onboardSubjectTitle).text.toString()
            val code = repo.subjects().firstOrNull { it.title == title }?.code
            val on = code != null && OnboardingDraft.subjects.contains(code)
            row.isSelected = on
            check.visibility = if (on) View.VISIBLE else View.INVISIBLE
        }
        // темы лежат внутри блоков-подкатегорий, поэтому идём по вложенным сеткам
        for (g in 0 until b.topicBox.childCount) {
            val group = b.topicBox.getChildAt(g)
            if (group !is GridLayout) continue
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as TextView
                chip.isSelected = OnboardingDraft.topics.contains(chip.text.toString())
            }
        }
        for (i in 0 until b.genderRow.childCount) {
            val chip = b.genderRow.getChildAt(i) as TextView
            val wanted = if (i == 0) Greetings.MALE else Greetings.FEMALE
            chip.isSelected = OnboardingDraft.gender == wanted
        }
        for (i in 0 until b.goalRow.childCount) {
            val chip = b.goalRow.getChildAt(i) as TextView
            chip.isSelected = goalLabels[i].first == OnboardingDraft.goal
        }
        for (i in 0 until b.dots.childCount) {
            val dot = b.dots.getChildAt(i)
            dot.setBackgroundResource(
                if (i == OnboardingDraft.step) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
            )
            val lp = dot.layoutParams as LinearLayout.LayoutParams
            lp.width = if (i == OnboardingDraft.step) dp(26) else dp(8)
            dot.layoutParams = lp
        }
        updateButton()
    }

    private fun updateButton() {
        b.btnNext.text = if (OnboardingDraft.step == 3) "Поехали!" else "Дальше"
        b.btnBack.alpha = if (OnboardingDraft.step == 0) 0.45f else 1f
        if (OnboardingDraft.step == 3) updateSummary()
    }

    private fun updateSummary() {
        val subjects = repo.subjects().filter { OnboardingDraft.subjects.contains(it.code) }
            .joinToString(", ") { it.title }
        val name = OnboardingDraft.name.ifBlank { "Игрок" }
        val topics = if (OnboardingDraft.topics.isEmpty()) "все темы"
        else OnboardingDraft.topics.joinToString(", ")
        b.summary.text = "$name, план готов:\nПредметы: ${subjects.ifBlank { "не выбраны" }}\n" +
            "Подтянем: $topics\nЦель: ${OnboardingDraft.goal} квеста в день"
    }

    private fun showStep(index: Int, animate: Boolean) {
        val list = steps
        val old = OnboardingDraft.step
        OnboardingDraft.step = index

        if (!animate) {
            list.forEachIndexed { i, v ->
                v.visibility = if (i == index) View.VISIBLE else View.GONE
                v.alpha = 1f
                v.translationX = 0f
            }
            syncSelections()
            return
        }

        val forward = index >= old
        val dir = if (forward) 1f else -1f
        val current = list[old]
        val next = list[index]

        current.animate().alpha(0f).translationX(-dir * 70f).setDuration(160).withEndAction {
            current.visibility = View.GONE
            current.translationX = 0f
            current.alpha = 1f
        }.start()

        next.alpha = 0f
        next.translationX = dir * 70f
        next.visibility = View.VISIBLE
        next.animate().alpha(1f).translationX(0f).setStartDelay(110).setDuration(280).start()
        syncSelections()
    }

    private fun onNext() {
        when (OnboardingDraft.step) {
            1 -> {
                val typed = b.nameInput.text.toString().trim()
                OnboardingDraft.name = typed
                if (typed.isEmpty()) {
                    Anim.shake(b.nameInput)
                    b.nameInput.error = "Введи имя"
                    return
                }
            }
            2 -> {
                if (OnboardingDraft.subjects.isEmpty()) {
                    Anim.shake(b.subjectBox)
                    return
                }
            }
        }
        if (OnboardingDraft.step < 3) showStep(OnboardingDraft.step + 1, animate = true) else finishOnboarding()
    }

    private fun finishOnboarding() {
        val name = OnboardingDraft.name.ifBlank { "Игрок" }
        val avatar = OnboardingDraft.avatar.ifBlank { SeedData.avatarChoices.first() }
        prefs.name = name
        prefs.avatar = avatar
        prefs.gender = OnboardingDraft.gender
        prefs.goalPerDay = OnboardingDraft.goal
        prefs.focusTopics = OnboardingDraft.topics.toSet()
        prefs.chosenSubjects = OnboardingDraft.subjects.toSet()
        prefs.saveThemeMode(
            if (b.themeSwitch.isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        prefs.soundHints = b.hintsSwitch.isChecked
        prefs.onboarded = true

        OnboardingDraft.reset()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun MutableSet<String>.toggleItem(value: String) {
        if (!this.add(value)) this.remove(value)
    }
}
