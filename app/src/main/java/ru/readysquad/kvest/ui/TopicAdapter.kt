package ru.readysquad.kvest.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.TheoryCard
import ru.readysquad.kvest.databinding.ItemTopicBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Icons
import ru.readysquad.kvest.util.SubjectIcons

/** Список карточек теории: тап раскрывает шпаргалку. */
class TopicAdapter(
    private var items: List<TheoryCard>,
    private val isLearned: (String) -> Boolean,
    private val onLearned: (TheoryCard) -> Unit
) : RecyclerView.Adapter<TopicAdapter.Holder>() {

    private val expanded = HashSet<Long>()

    inner class Holder(val b: ItemTopicBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<TheoryCard>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        val b = holder.b
        val ctx = b.root.context
        val isOpen = expanded.contains(card.id)
        val learned = isLearned(card.topic)

        b.topicIcon.setImageResource(SubjectIcons.of(card.subjectCode))
        b.topicTitle.text = card.topic
        b.topicMeta.text = "${card.subjectTitle} · ${card.exam}"
        b.topicTeaser.text = card.teaser
        b.topicBody.text = card.body
        b.topicBodyBox.visibility = if (isOpen) View.VISIBLE else View.GONE
        b.topicChevron.text = ""
        Icons.start(
            b.topicChevron,
            if (isOpen) R.drawable.ic_card_up else R.drawable.ic_card_down,
            R.color.on_bg_variant, 14
        )

        b.btnLearned.text = if (learned) "Повторено" else "Отметить как повторённое"
        if (learned) {
            Icons.start(b.btnLearned, R.drawable.ic_card_check, R.color.secondary, 14)
        } else {
            b.btnLearned.setCompoundDrawables(null, null, null, null)
        }
        b.btnLearned.alpha = if (learned) 0.6f else 1f
        // в стиле карточки ширина обводки 0, поэтому её нужно включить,
        // иначе подсветка повторённой темы не видна
        b.topicCard.strokeWidth = if (learned) dp(ctx, 2) else 0
        b.topicCard.strokeColor = ContextCompat.getColor(
            ctx, if (learned) R.color.secondary else R.color.outline
        )

        b.topicCard.setOnClickListener {
            if (isOpen) expanded.remove(card.id) else expanded.add(card.id)
            notifyItemChanged(position)
        }
        b.btnLearned.setOnClickListener {
            onLearned(card)
            Anim.pop(b.btnLearned, 0)
        }

        Anim.fadeInUp(b.topicCard, 40L * (position % 6), 16f)
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
