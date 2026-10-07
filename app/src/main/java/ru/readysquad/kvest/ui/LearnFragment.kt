package ru.readysquad.kvest.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.BadgeEngine
import ru.readysquad.kvest.data.CommonsApi
import ru.readysquad.kvest.data.Exam
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Stepik
import ru.readysquad.kvest.data.TheoryCard
import ru.readysquad.kvest.data.WikiApi
import ru.readysquad.kvest.databinding.FragmentLearnBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Dialogs
import ru.readysquad.kvest.util.ImageLoader
import java.util.concurrent.Executors

/**
 * Раздел «Обучение». Три вкладки: курсы Stepik, библиотека материалов
 * (свободный API Википедии) и шпаргалки. Первой открывается та, что выбрана
 * в настройках, — по умолчанию курсы Stepik.
 */
class LearnFragment : Fragment(R.layout.fragment_learn), Refreshable {

    private var _b: FragmentLearnBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }
    private val prefs by lazy { Prefs(requireContext()) }

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var mode = 0                     // 0 — библиотека, 1 — шпаргалки, 2 — курсы Stepik
    private var examFilter: String? = null
    private var searchToken = 0

    private lateinit var topicAdapter: TopicAdapter
    private val libraryAdapter = LibraryAdapter { hit -> showArticle(hit) }

    private companion object {
        const val IMAGES = 4
        const val COURSES = 8
    }

    private val quickTopics = listOf(
        "Теорема Пифагора", "Квадратное уравнение", "Проценты",
        "Причастие", "Знаки препинания", "Куликовская битва",
        "Крещение Руси", "Великая Отечественная война", "Отмена крепостного права"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentLearnBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        topicAdapter = TopicAdapter(
            items = emptyList(),
            isLearned = { prefs.learnedTopics.contains(it) },
            onLearned = { card -> markLearned(card) }
        )
        b.topicList.layoutManager = LinearLayoutManager(requireContext())
        b.topicList.adapter = topicAdapter
        b.resultList.layoutManager = LinearLayoutManager(requireContext())
        b.resultList.adapter = libraryAdapter

        b.btnSearch.setOnClickListener { search(b.queryInput.text.toString()) }
        b.queryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search(b.queryInput.text.toString())
                true
            } else {
                false
            }
        }

        // поиск курсов: своё поле, чтобы вкладка работала и без библиотеки
        b.stepikSearch.setOnClickListener { searchCourses(b.stepikInput.text.toString()) }
        b.stepikInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchCourses(b.stepikInput.text.toString())
                true
            } else {
                false
            }
        }

        // шпаргалка из открытого источника: короткая выжимка из Википедии
        b.theorySearch.setOnClickListener { addTheoryFromSource() }
        b.theoryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                addTheoryFromSource()
                true
            } else {
                false
            }
        }

        buildModeChips()
        buildQuickChips()
        buildFilters()
        showMode(defaultMode())
        refresh()
    }

    /**
     * Ищет тему в открытых источниках и добавляет найденное в шпаргалки.
     * Текст берётся из Википедии в три предложения и сохраняется в базу,
     * поэтому шпаргалка остаётся доступной и без интернета.
     */
    private fun addTheoryFromSource() {
        val bind = _b ?: return
        val query = bind.theoryInput.text.toString().trim()
        if (query.isEmpty()) {
            Anim.shake(bind.theoryInput)
            return
        }
        bind.theoryStatus.visibility = View.VISIBLE
        bind.theoryStatus.text = "Ищем «$query» в открытых источниках…"
        bind.theorySearch.isEnabled = false

        io.execute {
            val result = try {
                WikiApi.search(query, 1).firstOrNull()
            } catch (e: Exception) {
                null
            }
            ui.post {
                if (_b == null) return@post
                bind.theorySearch.isEnabled = true
                if (result == null || result.extract.isBlank()) {
                    bind.theoryStatus.text =
                        "Ничего не нашлось. Попробуй другую формулировку темы."
                    return@post
                }
                val subject = prefs.chosenSubjects.firstOrNull() ?: "math"
                val saved = repo.addTheory(
                    subjectCode = subject,
                    topic = result.title,
                    exam = examFilter ?: Exam.OGE,
                    teaser = result.extract.take(110),
                    body = result.extract + "\n\nИсточник: Википедия · " + result.url
                )
                bind.theoryStatus.text = if (saved) {
                    "Добавлено в шпаргалки: «${result.title}». Листай список ниже."
                } else {
                    "Не удалось сохранить шпаргалку."
                }
                bind.theoryInput.setText("")
                refresh()
            }
        }
    }

    override fun refresh() {
        val bind = _b ?: return
        val cards = repo.theory(exam = examFilter)
        topicAdapter.submit(cards)
        bind.learnEmpty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE

        val total = repo.theory().size
        val learned = prefs.learnedTopics.size.coerceAtMost(total)
        val percent = if (total == 0) 0 else learned * 100 / total
        bind.learnCount.text = "Повторено тем: $learned из $total"
        bind.learnPercent.text = "$percent%"
        bind.learnBar.setProgress(percent, true)
    }

    // ------------------------------------------------------------------ режимы

    private fun buildModeChips() {
        val bind = _b ?: return
        bind.modeRow.removeAllViews()
        modeOrder().forEach { (label, id) ->
            val chip = chip(label, id == mode) {
                showMode(id)
                Anim.pop(it, 0)
            }
            bind.modeRow.addView(chip)
        }
    }

    /**
     * Порядок вкладок. Первой идёт та, что выбрана в настройках:
     * по умолчанию курсы Stepik, по желанию — статьи Википедии.
     */
    private fun modeOrder(): List<Pair<String, Int>> = if (prefs.learnSource == Prefs.LEARN_WIKI) {
        listOf("Библиотека" to 0, "Курсы" to 2, "Шпаргалки" to 1)
    } else {
        listOf("Курсы" to 2, "Библиотека" to 0, "Шпаргалки" to 1)
    }

    /** Вкладка, которая открывается по умолчанию. */
    private fun defaultMode(): Int = if (prefs.learnSource == Prefs.LEARN_WIKI) 0 else 2

    private fun showMode(index: Int) {
        mode = index
        val bind = _b ?: return
        bind.libraryBlock.visibility = if (index == 0) View.VISIBLE else View.GONE
        bind.theoryBlock.visibility = if (index == 1) View.VISIBLE else View.GONE
        bind.stepikBlock.visibility = if (index == 2) View.VISIBLE else View.GONE
        buildModeChips()

        val initial = prefs.focusTopics.firstOrNull()?.takeIf { it.isNotBlank() } ?: "ОГЭ"

        if (index == 0 && libraryAdapter.itemCount == 0) {
            // библиотека должна быть уже открыта и заполнена
            bind.queryInput.setText(initial)
            search(initial)
        }
        if (index == 2 && bind.stepikList.childCount == 0) {
            // курсы подгружаем сразу, чтобы вкладка не открывалась пустой
            bind.stepikInput.setText(initial)
            searchCourses(initial)
        }
    }

    // ------------------------------------------------------------------ библиотека

    private fun buildQuickChips() {
        val bind = _b ?: return
        bind.chipRow.removeAllViews()
        quickTopics.forEach { topic ->
            bind.chipRow.addView(
                chip(topic, false) {
                    bind.queryInput.setText(topic)
                    search(topic)
                    Anim.pop(it, 0)
                }
            )
        }
    }

    private fun search(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return

        // во вкладке «Курсы» тот же поиск ищет курсы Stepik, а не статьи
        if (mode == 2) {
            searchCourses(query)
            return
        }

        searchToken++
        val myToken = searchToken
        val bind = _b ?: return
        bind.progress.visibility = View.VISIBLE
        bind.btnSearch.isEnabled = false

        io.execute {
            // три источника: статьи Википедии, толкование из Викисловаря и картинки из Commons
            val hits = try {
                WikiApi.search(query)
            } catch (e: Exception) {
                null
            }
            val definition = try {
                WikiApi.definition(query)
            } catch (e: Exception) {
                ""
            }
            val pictures = try {
                CommonsApi.images(query, IMAGES)
            } catch (e: Exception) {
                emptyList()
            }

            ui.post {
                if (myToken != searchToken || _b == null) return@post
                bind.progress.visibility = View.GONE
                bind.btnSearch.isEnabled = true
                when {
                    hits == null -> {
                        hideExtras()
                        status(
                            "Не удалось загрузить материалы: нет подключения к интернету.\n" +
                                "Шпаргалки на соседней вкладке работают офлайн."
                        )
                    }
                    hits.isEmpty() -> {
                        hideExtras()
                        status("Ничего не найдено. Попробуй другой запрос.")
                    }
                    else -> {
                        bind.statusText.visibility = View.GONE
                        libraryAdapter.submit(hits)
                        renderDefinition(definition)
                        renderImages(pictures)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ курсы Stepik

    /**
     * Курсы по теме из открытого каталога Stepik.
     * Курсы не скачиваются: пользователь открывает их на сайте.
     */
    private fun searchCourses(query: String) {
        searchToken++
        val myToken = searchToken
        val bind = _b ?: return
        bind.progress.visibility = View.VISIBLE
        bind.btnSearch.isEnabled = false
        bind.stepikStatus.visibility = View.VISIBLE
        bind.stepikStatus.text = "Ищем курсы по теме «$query»…"

        io.execute {
            var result = try {
                Stepik.search(query, COURSES)
            } catch (e: Exception) {
                Stepik.Result(emptyList(), "Stepik недоступен. Шпаргалки на соседней вкладке работают офлайн.")
            }
            // по длинному запросу каталог часто отвечает пусто: пробуем по первому слову
            if (result.courses.isEmpty() && query.contains(' ')) {
                val short = query.substringBefore(' ').trim()
                if (short.isNotEmpty()) {
                    result = try {
                        Stepik.search(short, COURSES)
                    } catch (e: Exception) {
                        result
                    }
                }
            }
            ui.post {
                if (myToken != searchToken || _b == null) return@post
                bind.progress.visibility = View.GONE
                bind.btnSearch.isEnabled = true
                bind.stepikList.removeAllViews()
                if (result.courses.isEmpty()) {
                    bind.stepikStatus.visibility = View.VISIBLE
                    bind.stepikStatus.text = result.error
                        ?: "По запросу «$query» курсов не нашлось. Попробуй, например, «ОГЭ математика»."
                    return@post
                }
                bind.stepikStatus.visibility = View.GONE
                result.courses.forEachIndexed { i, course ->
                    val row = courseRow(course)
                    bind.stepikList.addView(row)
                    Anim.fadeInUp(row, 60L * i, 14f)
                }
            }
        }
    }

    /** Карточка курса: обложка, название, описание и переход на Stepik. */
    private fun courseRow(course: Stepik.Course): View {
        val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface2))
            isClickable = true
            isFocusable = true
            setOnClickListener { openCourse(course) }
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val cover = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_stat)
        }
        course.cover?.let { ImageLoader.load(it, cover) }
        row.addView(cover)

        val column = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(12) }
        }
        column.addView(TextView(requireContext()).apply {
            text = course.title
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_bg))
        })
        if (course.summary.isNotBlank()) {
            column.addView(TextView(requireContext()).apply {
                text = course.summary.take(180)
                textSize = 12f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_bg_variant))
                setPadding(0, dp(4), 0, 0)
            })
        }
        val meta = buildString {
            if (course.learners > 0) append("${course.learners} записались")
            if (!course.isPaid) append(if (isEmpty()) "бесплатно" else " · бесплатно")
        }
        if (meta.isNotBlank()) {
            column.addView(TextView(requireContext()).apply {
                text = meta
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
                setPadding(0, dp(5), 0, 0)
            })
        }
        row.addView(column)
        card.addView(row)
        return card
    }

    /** Открывает курс на сайте Stepik. */
    private fun openCourse(course: Stepik.Course) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(course.url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    /** Короткое толкование из Викисловаря. */
    private fun renderDefinition(text: String) {
        val bind = _b ?: return
        if (text.isBlank()) {
            bind.defCard.visibility = View.GONE
        } else {
            bind.defCard.visibility = View.VISIBLE
            bind.defText.text = text
        }
    }

    /** Картинки по теме из Wikimedia Commons. */
    private fun renderImages(pictures: List<CommonsApi.Picture>) {
        val bind = _b ?: return
        bind.imageRow.removeAllViews()
        if (pictures.isEmpty()) {
            bind.imageScroll.visibility = View.GONE
            return
        }
        bind.imageScroll.visibility = View.VISIBLE
        val width = dp(132)
        val height = dp(92)
        pictures.forEach { picture ->
            val image = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_stat)
                clipToOutline = true
                contentDescription = picture.title
            }
            val lp = LinearLayout.LayoutParams(width, height)
            lp.marginEnd = dp(8)
            image.layoutParams = lp
            // нажатие открывает иллюстрацию целиком
            image.setOnClickListener {
                Dialogs.image(requireContext(), picture.url, picture.title)
                Anim.pop(image, 0)
            }
            bind.imageRow.addView(image)
            ImageLoader.load(picture.url, image)
        }
    }

    private fun hideExtras() {
        val bind = _b ?: return
        bind.imageScroll.visibility = View.GONE
        bind.defCard.visibility = View.GONE
    }

    private fun status(text: String) {
        val bind = _b ?: return
        libraryAdapter.submit(emptyList())
        bind.statusText.visibility = View.VISIBLE
        bind.statusText.text = text
        Anim.fadeInUp(bind.statusText, 0, 14f)
    }

    private fun showArticle(hit: WikiApi.Hit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(hit.title)
            .setMessage(if (hit.extract.isBlank()) "Открой статью, чтобы прочитать материал." else hit.extract)
            .setNegativeButton("Закрыть", null)
            .setPositiveButton("Открыть статью") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.url)))
            }
            .show()
    }

    // ------------------------------------------------------------------ шпаргалки

    private fun buildFilters() {
        val bind = _b ?: return
        bind.examRow.removeAllViews()
        val options = listOf<Pair<String?, String>>(
            null to "Все", Exam.OGE to "ОГЭ", Exam.EGE to "ЕГЭ", Exam.VPR to "ВПР"
        )
        options.forEach { (value, label) ->
            bind.examRow.addView(
                chip(label, value == examFilter, padded = true) {
                    examFilter = value
                    buildFilters()
                    refresh()
                    Anim.pop(it, 0)
                }
            )
        }
    }

    private fun markLearned(card: TheoryCard) {
        val count = prefs.markLearned(card.topic)
        val newBadges = BadgeEngine.evaluateTheory(repo, count)
        refresh()
        if (newBadges.isNotEmpty()) {
            Toast.makeText(
                requireContext(),
                "Новый бейдж: " + newBadges.joinToString { it.title },
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(requireContext(), "Тема «${card.topic}» повторена", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ утилиты

    private fun chip(label: String, selected: Boolean, padded: Boolean = false, onClick: (View) -> Unit): TextView =
        TextView(requireContext()).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            val h = dp(if (padded) 16 else 14)
            setPadding(h, dp(9), h, dp(9))
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_choice)
            setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text))
            isSelected = selected
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick(this) }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            layoutParams = lp
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        searchToken++
        _b = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
