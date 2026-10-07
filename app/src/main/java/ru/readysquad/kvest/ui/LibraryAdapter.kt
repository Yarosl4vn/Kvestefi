package ru.readysquad.kvest.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.readysquad.kvest.data.WikiApi
import ru.readysquad.kvest.databinding.ItemLibraryBinding
import ru.readysquad.kvest.util.Anim

/** Результаты поиска по библиотеке материалов. */
class LibraryAdapter(
    private val onClick: (WikiApi.Hit) -> Unit
) : RecyclerView.Adapter<LibraryAdapter.Holder>() {

    private var items: List<WikiApi.Hit> = emptyList()

    inner class Holder(val b: ItemLibraryBinding) : RecyclerView.ViewHolder(b.root)

    fun submit(list: List<WikiApi.Hit>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val hit = items[position]
        val b = holder.b
        b.hitTitle.text = hit.title
        b.hitText.text = if (hit.extract.isBlank()) "Текст статьи доступен по ссылке" else hit.extract
        b.hitRoot.setOnClickListener { onClick(hit) }
        Anim.fadeInUp(b.hitRoot, 30L * (position % 8), 14f)
    }
}
