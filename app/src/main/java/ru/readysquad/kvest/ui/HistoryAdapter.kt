package ru.readysquad.kvest.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.readysquad.kvest.data.Attempt
import ru.readysquad.kvest.data.TimeFmt
import ru.readysquad.kvest.databinding.ItemHistoryBinding
import ru.readysquad.kvest.util.SubjectIcons

/** Список прошлых попыток. */
class HistoryAdapter(
    private var items: List<Attempt>,
    private val onClick: (Attempt) -> Unit = {}
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    inner class Holder(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<Attempt>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val a = items[position]
        val b = holder.b
        b.histIcon.setImageResource(SubjectIcons.of(a.subjectCode))
        b.histTitle.text = "${a.subjectTitle} · ${TimeFmt.short(a.createdAt)}"
        b.histMeta.text = "${a.correct} из ${a.total} · ${TimeFmt.duration(a.seconds)} · ${a.accuracy}%"
        b.histScore.text = "+${a.score}"
        b.histGrade.text = "оценка ${a.grade}"
        // попытку можно открыть и посмотреть задания с ответами
        b.histRoot.setOnClickListener { onClick(a) }
    }
}
