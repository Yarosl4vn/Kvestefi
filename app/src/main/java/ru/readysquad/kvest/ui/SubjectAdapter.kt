package ru.readysquad.kvest.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.readysquad.kvest.data.Subject
import ru.readysquad.kvest.data.TaskGenerator
import ru.readysquad.kvest.databinding.ItemSubjectBinding
import ru.readysquad.kvest.util.SubjectIcons

/**
 * Список предметов.
 *
 * Тап по карточке — квест по предмету (сначала задания открытого банка,
 * без интернета — встроенный набор).
 * «Тренировка» — задания строит генератор на устройстве, поэтому они бесконечные.
 *
 * @param realMode раздел «Реальные задания»: квест идёт по краткому ответу,
 *   тренировка там не нужна
 */
class SubjectAdapter(
    private val items: List<Subject>,
    private val best: Map<String, Int>,
    private val runs: Map<String, Int>,
    private val realMode: Boolean,
    private val onlineCodes: Set<String> = emptySet(),
    private val onShortfall: (Subject) -> Unit = {},
    private val onClick: (Subject) -> Unit,
    private val onTrain: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectAdapter.Holder>() {

    inner class Holder(val b: ItemSubjectBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = items[position]
        val b = holder.b
        b.subjectIcon.setImageResource(SubjectIcons.of(s.code))
        b.subjectTitle.text = s.title
        b.subjectTopics.text = s.topics.replace(", ", " · ")

        if (realMode) {
            // у части предметов онлайн-заданий с кратким ответом нет — говорим прямо
            b.subjectProgress.text = if (onlineCodes.contains(s.code)) {
                "Настоящие задания ОГЭ и ЕГЭ · ввод ответа в клетки"
            } else {
                "Онлайн-заданий нет · откроется встроенный набор"
            }
        } else {
            val bestScore = best[s.code]
            val count = runs[s.code] ?: 0
            b.subjectProgress.text = when {
                bestScore == null -> "Ещё не проходил"
                else -> "Лучший результат: $bestScore очков · попыток: $count"
            }
        }

        // «Тренировка» — только там, где генератор умеет строить задания,
        // и только в обычном разделе
        b.btnTrain.visibility = if (!realMode && TaskGenerator.supports(s.code)) View.VISIBLE else View.GONE

        b.subjectCard.setOnClickListener {
            if (realMode && !onlineCodes.contains(s.code)) onShortfall(s)
            onClick(s)
        }
        b.btnTrain.setOnClickListener { onTrain(s) }
    }
}
