package ru.readysquad.kvest.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.fragment.app.Fragment
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.Repository
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.databinding.FragmentBadgesBinding
import ru.readysquad.kvest.databinding.ItemBadgeBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.Dialogs
import ru.readysquad.kvest.util.Notifier

/**
 * Экран достижений.
 *
 * Сетку собираем сами, а не через RecyclerView: список вложен в ScrollView,
 * и RecyclerView с wrap_content измерялся по первой видимой части — доходило
 * до того, что из 13 бейджей показывались 6. GridLayout измеряется целиком.
 */
class BadgesFragment : Fragment(R.layout.fragment_badges), Refreshable {

    private var _b: FragmentBadgesBinding? = null
    private val b get() = _b!!
    private val repo by lazy { Repository.get(requireContext()) }
    private val prefs by lazy { Prefs(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentBadgesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refresh()
    }

    override fun refresh() {
        val bind = _b ?: return
        val badges = repo.badges()
        val unlocked = badges.count { it.isUnlocked }
        val percent = if (badges.isEmpty()) 0 else unlocked * 100 / badges.size

        bind.badgeCount.text = "$unlocked из ${badges.size} открыто"
        bind.badgePercent.text = "$percent%"
        bind.badgeBar.setProgress(percent, true)

        // достижения открыты — метку «новое» на вкладке снимаем
        Notifier.markBadgesSeen(prefs, badges)
        fillGrid(bind.badgeGrid, badges)
    }

    private fun fillGrid(grid: GridLayout, badges: List<Badge>) {
        grid.removeAllViews()
        val density = resources.displayMetrics.density
        val gap = (6 * density).toInt()

        badges.forEachIndexed { index, badge ->
            val row = ItemBadgeBinding.inflate(layoutInflater, grid, false)
            BadgeAdapter.bind(row, badge, index)
            // нажатие открывает описание: как получить и когда получено
            row.badgeRoot.setOnClickListener { Dialogs.badge(requireContext(), badge) }

            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.setMargins(gap, gap, gap, gap)
            row.root.layoutParams = lp
            grid.addView(row.root)
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
