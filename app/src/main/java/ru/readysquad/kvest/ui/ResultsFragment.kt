package ru.readysquad.kvest.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Attempt
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.databinding.FragmentResultsBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Dialogs
import ru.readysquad.kvest.util.Icons

/** «Мои результаты»: сводка, график прогресса и история всех квестов. */
class ResultsFragment : Fragment(R.layout.fragment_results), Refreshable {

    private var _b: FragmentResultsBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }
    private val adapter = HistoryAdapter(emptyList()) { attempt -> openAttempt(attempt) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentResultsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.historyList.layoutManager = LinearLayoutManager(requireContext())
        b.historyList.adapter = adapter
        refresh()
    }

    override fun refresh() {
        val bind = _b ?: return
        val stats = repo.stats()

        bind.miniScore.text = stats.totalScore.toString()
        bind.miniBest.text = stats.bestScore.toString()
        bind.miniAccuracy.text = "${stats.accuracy}%"

        val points = repo.progress()
        bind.chart.setData(points)

        // Тренд: средняя точность последних трёх попыток против предыдущих трёх
        val attempts = repo.attempts(30)
        val recent = attempts.take(3)
        val older = attempts.drop(3).take(3)
        if (recent.isEmpty()) {
            bind.trendChip.text = "нет данных"
            bind.trendChip.setCompoundDrawables(null, null, null, null)
            bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_bg_variant))
        } else if (older.isEmpty()) {
            bind.trendChip.text = "старт"
            Icons.start(bind.trendChip, R.drawable.ic_card_rocket, R.color.secondary, 14)
            bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.secondary))
        } else {
            val r = recent.map { it.accuracy }.average()
            val o = older.map { it.accuracy }.average()
            val diff = (r - o).toInt()
            when {
                diff > 3 -> {
                    bind.trendChip.text = "растём +$diff%"
                    Icons.start(bind.trendChip, R.drawable.ic_card_up, R.color.success, 14)
                    bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                }
                diff < -3 -> {
                    bind.trendChip.text = "сдаём $diff%"
                    Icons.start(bind.trendChip, R.drawable.ic_card_down, R.color.danger, 14)
                    bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.danger))
                }
                else -> {
                    bind.trendChip.text = "ровно"
                    Icons.start(bind.trendChip, R.drawable.ic_card_flat, R.color.accent, 14)
                    bind.trendChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent))
                }
            }
        }

        adapter.submit(attempts)
        bind.historyEmpty.visibility = if (attempts.isEmpty()) View.VISIBLE else View.GONE

        Anim.fadeInUp(bind.chart, 100)
    }

    /** Попытка: задания, ответы игрока и верные ответы. */
    private fun openAttempt(attempt: Attempt) {
        Dialogs.attempt(
            requireContext(),
            attempt,
            repo.attemptAnswers(attempt.id),
            attempt.subjectTitle
        ) { openSubjectStats(attempt.subjectCode, attempt.subjectTitle) }
    }

    /** Статистика по предмету: попытки, динамика результата и темы. */
    private fun openSubjectStats(code: String, title: String) {
        val attempts = repo.attempts(60).filter { it.subjectCode == code }
        Dialogs.subjectStats(requireContext(), title, attempts, repo.topicStats(code)) { a ->
            Dialogs.attempt(requireContext(), a, repo.attemptAnswers(a.id), title)
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
