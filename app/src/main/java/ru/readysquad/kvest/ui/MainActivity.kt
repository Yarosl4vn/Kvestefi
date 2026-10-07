package ru.readysquad.kvest.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.readysquad.kvest.R
import ru.readysquad.kvest.util.Greetings
import ru.readysquad.kvest.util.Notifier
import ru.readysquad.kvest.util.Palettes
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.databinding.ActivityMainBinding
import ru.readysquad.kvest.databinding.DialogHelloBinding

/**
 * Главный экран: нижняя навигация между разделами.
 * Разделы держим в памяти (hide/show), чтобы не терять прокрутку.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { Prefs(this) }
    private val repo by lazy { Repository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // применяем выбранную палитру до создания разметки
        Palettes.apply(this, Prefs(this).palette)

        if (!prefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            // метка «есть новое» обновляется при каждом переходе
            updateNavBadge()
            true
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            (supportFragmentManager.findFragmentByTag(tagOf(item.itemId)) as? Refreshable)?.refresh()
        }

        // Важно: первый пункт меню уже отмечен, поэтому selectedItemId не вызовет
        // onItemSelected — раздел добавляем явно, иначе экран останется пустым.
        val startItem = if (savedInstanceState == null) {
            R.id.tab_home
        } else {
            binding.bottomNav.selectedItemId.takeIf { it != 0 } ?: R.id.tab_home
        }
        binding.bottomNav.selectedItemId = startItem
        show(startItem)
        updateNavBadge()

        // приветствие при каждом входе в приложение (не при повороте экрана)
        if (savedInstanceState == null) showGreeting()
    }

    /** Красная метка на вкладке достижений, пока есть непросмотренные бейджи. */
    private fun updateNavBadge() {
        val fresh = Notifier.newBadges(prefs, repo.badges())
        if (fresh.isEmpty()) {
            binding.bottomNav.removeBadge(R.id.tab_badges)
        } else {
            binding.bottomNav.getOrCreateBadge(R.id.tab_badges).apply {
                backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.danger)
                badgeTextColor = android.graphics.Color.WHITE
                maxCharacterCount = 2
            }
        }
    }

    /** Короткое приветствие с серией дней: показывается один раз за запуск. */
    private fun showGreeting() {
        val view = DialogHelloBinding.inflate(layoutInflater)
        val stats = repo.stats()
        view.helloTitle.text = Greetings.helloTitle(prefs.name, prefs.gender)
        view.helloText.text = Greetings.helloText(prefs.gender, stats.quests, prefs.streak)
        view.helloStreak.text = Greetings.streakLine(prefs.streak, prefs.bestDayStreak)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view.root)
            .create()
        view.helloButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        // Экран мог быть закрыт сразу в onCreate (переход в онбординг).
        if (!::binding.isInitialized) return
        val id = binding.bottomNav.selectedItemId
        (supportFragmentManager.findFragmentByTag(tagOf(id)) as? Refreshable)?.refresh()
        updateNavBadges()
    }

    /** Переключить раздел из кода (например, из карточки на главной). */
    fun openTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
        show(itemId)
    }

    private fun tagOf(itemId: Int): String = when (itemId) {
        R.id.tab_quests -> "quests"
        R.id.tab_learn -> "learn"
        R.id.tab_results -> "results"
        R.id.tab_badges -> "badges"
        else -> "home"
    }

    private fun show(itemId: Int) {
        val tag = tagOf(itemId)
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setCustomAnimations(
            android.R.anim.fade_in, android.R.anim.fade_out
        )
        fm.fragments.forEach { tx.hide(it) }

        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(R.id.navHost, newFragment(tag), tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
        fm.executePendingTransactions()
        (fm.findFragmentByTag(tag) as? Refreshable)?.refresh()
    }

    private fun newFragment(tag: String): Fragment = when (tag) {
        "quests" -> SubjectsFragment()
        "learn" -> LearnFragment()
        "results" -> ResultsFragment()
        "badges" -> BadgesFragment()
        else -> HomeFragment()
    }

    /** Подсказка на иконке раздела: сколько бейджей уже открыто. */
    private fun updateNavBadges() {
        val nav = binding.bottomNav
        val unlocked = repo.unlockedCount()
        val total = repo.badges().size
        // Цвета бейджа берём из темы, чтобы он читался и в светлой, и в тёмной теме.
        if (unlocked in 1 until total) {
            nav.getOrCreateBadge(R.id.tab_badges).apply {
                number = unlocked
                isVisible = true
            }
        } else {
            nav.removeBadge(R.id.tab_badges)
        }
    }
}
