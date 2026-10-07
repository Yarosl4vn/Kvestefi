package ru.readysquad.kvest.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.util.Avatars
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.SeedData
import ru.readysquad.kvest.data.Subject
import ru.readysquad.kvest.databinding.ActivitySettingsBinding
import ru.readysquad.kvest.databinding.ItemOnboardSubjectBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Greetings
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

/** Профиль и настройки: имя, аватар, цель, тема, предметы и слабые темы. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private val prefs by lazy { Prefs(this) }
    private val repo by lazy { Repository.get(this) }

    private val goalLabels = listOf(1 to "1 квест", 3 to "3 квеста", 5 to "5 квестов")
    private var subjects: List<Subject> = emptyList()

    /** Выбор своей иконки профиля из галереи. */
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = Avatars.save(this, uri)
        if (name == null) {
            Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
        } else {
            prefs.avatarImage = name
            updateAvatarPreview()
            Toast.makeText(this, "Своя иконка сохранена", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // применяем выбранную палитру до создания разметки
        Palettes.apply(this, Prefs(this).palette)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        subjects = repo.subjects()

        b.btnBack.setOnClickListener { finish() }
        b.btnDone.setOnClickListener { finish() }
        b.btnReset.setOnClickListener { confirmReset() }

        b.nameInput.setText(prefs.name)
        b.nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.trim().orEmpty()
                if (v.isNotEmpty()) prefs.name = v
            }
        })

        b.themeSwitch.isChecked = prefs.themeMode == AppCompatDelegate.MODE_NIGHT_YES
        b.hintsSwitch.isChecked = prefs.soundHints

        b.themeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.saveThemeMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        }
        b.hintsSwitch.setOnCheckedChangeListener { _, checked -> prefs.soundHints = checked }

        b.btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        b.btnClearImage.setOnClickListener {
            Avatars.clear(this)
            prefs.avatarImage = null
            updateAvatarPreview()
            Toast.makeText(this, "Своя иконка убрана", Toast.LENGTH_SHORT).show()
        }

        buildAvatars()
        buildGender()
        buildGoals()
        buildPalette()
        buildSources()
        buildSubjects()
        buildTopics()
        updateAvatarPreview()
        updateAbout()

        Anim.fadeInUp(b.nameInput, 80)
    }

    // ------------------------------------------------------------------ блоки

    private fun buildAvatars() {
        b.avatarGrid.removeAllViews()
        val size = dp(54)
        SeedData.avatarChoices.forEach { emoji ->
            val tv = TextView(this).apply {
                text = emoji
                textSize = 23f
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_avatar_choice)
                isSelected = emoji == prefs.avatar
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.avatar = emoji
                    // выбор эмодзи отменяет свою картинку, иначе на главной
                    // продолжала бы показываться она
                    if (prefs.avatarImage != null) {
                        Avatars.clear(this@SettingsActivity)
                        prefs.avatarImage = null
                        updateAvatarPreview()
                    }
                    buildAvatars()
                    Anim.pop(this, 0)
                }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
                lp.width = size
            lp.height = size
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            tv.layoutParams = lp
            b.avatarGrid.addView(tv)
        }
    }

    private fun buildGoals() {
        b.goalRow.removeAllViews()
        goalLabels.forEach { (value, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(13), dp(6), dp(13))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                isSelected = value == prefs.goalPerDay
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.goalPerDay = value
                    buildGoals()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.goalRow.addView(chip)
        }
    }

    /** Пол: меняет только род в приветствии. */
    private fun buildGender() {
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
                setPadding(dp(6), dp(13), dp(6), dp(13))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                isSelected = prefs.gender == value
                isClickable = true
                isFocusable = true
                Icons.start(this, icon, R.color.chip_text, 15)
                setOnClickListener {
                    prefs.gender = value
                    buildGender()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.genderRow.addView(chip)
        }
    }

    /** Что открывать первым в «Обучении»: курсы Stepik или статьи Википедии. */
    private fun buildSources() {
        b.sourceRow.removeAllViews()
        val options = listOf(
            Prefs.LEARN_STEPIK to "Курсы Stepik",
            Prefs.LEARN_WIKI to "Википедия"
        )
        options.forEach { (value, label) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(13), dp(6), dp(13))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_chip_choice)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                isSelected = value == prefs.learnSource
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.learnSource = value
                    buildSources()
                    Anim.pop(this, 0)
                }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(5), 0, dp(5), 0)
            chip.layoutParams = lp
            b.sourceRow.addView(chip)
        }
    }

    private fun buildSubjects() {
        b.subjectBox.removeAllViews()
        val chosen = prefs.chosenSubjects.toMutableSet()
        subjects.forEach { s ->
            val row = ItemOnboardSubjectBinding.inflate(layoutInflater, b.subjectBox, false)
            row.onboardSubjectIcon.setImageResource(SubjectIcons.of(s.code))
            row.onboardSubjectTitle.text = s.title
            row.onboardSubjectTopics.text = s.topics.replace(", ", " · ")
            val apply = {
                val on = chosen.add(s.code).let { if (it) true else { chosen.remove(s.code); false } }
                row.root.isSelected = on
                row.onboardSubjectCheck.visibility = if (on) View.VISIBLE else View.INVISIBLE
                prefs.chosenSubjects = chosen.toSet()
                // темы зависят от выбранных предметов
                buildTopics()
                Anim.release(row.root)
            }
            val initiallyOn = chosen.contains(s.code)
            row.root.isSelected = initiallyOn
            row.onboardSubjectCheck.visibility = if (initiallyOn) View.VISIBLE else View.INVISIBLE
            row.root.setOnClickListener { apply() }
            b.subjectBox.addView(row.root)
        }
    }

    /** Слабые темы — подкатегориями по предметам, как и в приветствии. */
    private fun buildTopics() {
        b.topicBox.removeAllViews()
        val focus = prefs.focusTopics.toMutableSet()
        val codes = if (prefs.chosenSubjects.isEmpty()) null else prefs.chosenSubjects
        val subjects = repo.subjects().filter { codes == null || codes.contains(it.code) }

        subjects.forEach { subject ->
            val topics = SeedData.topicsBySubject[subject.code].orEmpty()
            if (topics.isEmpty()) return@forEach

            b.topicBox.addView(
                TextView(this).apply {
                    text = subject.title
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                    setPadding(dp(2), dp(14), 0, dp(6))
                    Icons.start(this, SubjectIcons.of(subject.code), R.color.on_bg, 15)
                }
            )

            val grid = GridLayout(this).apply { columnCount = 2 }
            topics.forEach { topic ->
                val chip = TextView(this).apply {
                    text = topic
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_topic_choice)
                    setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.chip_text))
                    isSelected = focus.contains(topic)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (!focus.add(topic)) focus.remove(topic)
                        isSelected = focus.contains(topic)
                        prefs.focusTopics = focus.toSet()
                        Anim.pop(this, 0)
                    }
                }
                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                )
                lp.width = 0
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                chip.layoutParams = lp
                grid.addView(chip)
            }
            b.topicBox.addView(grid)
        }
    }

    private fun updateAbout() {
        val stats = repo.stats()
        b.dataHint.text = "Пройдено квестов: ${stats.quests} · очков: ${stats.totalScore} · " +
            "открыто бейджей: ${repo.unlockedCount()} из ${repo.badges().size}.\n" +
            "Вся статистика хранится только на устройстве, в локальной базе SQLite."
        b.about.text = "Квестефи 1.7.1\n" +
            "Реальные задания приходят из открытого банка в интернете и не сохраняются в приложении. " +
            "Без интернета работают ${SeedData.tasks.size} встроенных заданий, генератор тренировки, " +
            "теория и статистика.\n" +
            "Официального API у ФИПИ нет: открытые банки заданий — это веб-приложения " +
            "без публичного интерфейса. Свой источник подключается через интерфейс TaskSource."
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Сбросить прогресс?")
            .setMessage("История, очки, бейджи и настройки будут очищены. Действие необратимо.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сбросить") { _, _ ->
                repo.resetAll()
                OnboardingDraft.reset()
                prefs.bestStreak = 0
                prefs.bestDayStreak = 0
                prefs.streak = 0
                prefs.lastActiveDay = ""
                prefs.activeDays = emptySet()
                prefs.learnedTopics = emptySet()
                prefs.onboarded = false
                startActivity(Intent(this, OnboardingActivity::class.java))
                // закрываем весь стек, чтобы старая главная не осталась внизу
                finishAffinity()
            }
            .show()
    }

    // ------------------------------------------------------------------ палитра и иконка

    private fun buildPalette() {
        b.paletteRow.removeAllViews()
        val size = dp(44)
        Palettes.all.forEach { item ->
            val color = ContextCompat.getColor(this, item.swatch)
            val selected = prefs.palette == item.key
            val swatch = View(this).apply {
                background = if (selected) {
                    ringDrawable(color, ContextCompat.getColor(this@SettingsActivity, R.color.on_bg))
                } else {
                    circleDrawable(color)
                }
                isClickable = true
                isFocusable = true
                contentDescription = item.title
                setOnClickListener {
                    if (prefs.palette == item.key) return@setOnClickListener
                    prefs.palette = item.key
                    // пересоздаём экран, чтобы палитра применилась сразу
                    recreate()
                }
            }
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = dp(10)
            swatch.layoutParams = lp
            b.paletteRow.addView(swatch)
        }
        b.paletteHint.text = "Текущий цвет: ${Palettes.titleOf(prefs.palette)}"
    }

    private fun circleDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun ringDrawable(color: Int, ring: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(3), ring)
    }

    private fun updateAvatarPreview() {
        val bitmap = Avatars.load(this, prefs.avatarImage)
        if (bitmap != null) {
            b.avatarPreview.setImageBitmap(bitmap)
            b.avatarPreview.visibility = View.VISIBLE
            b.btnClearImage.visibility = View.VISIBLE
        } else {
            b.avatarPreview.visibility = View.GONE
            b.btnClearImage.visibility = View.GONE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
