package ru.readysquad.kvest.ui

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Badge
import ru.readysquad.kvest.data.TimeFmt
import ru.readysquad.kvest.databinding.ItemBadgeBinding
import ru.readysquad.kvest.util.Anim
import ru.readysquad.kvest.util.BadgeIcons
import ru.readysquad.kvest.util.ThemeColors

/**
 * Наполнение карточки достижения: открытые светятся, закрытые приглушены.
 *
 * Раньше это был адаптер RecyclerView, но сетка достижений живёт внутри
 * ScrollView, и RecyclerView показывал только первую часть списка.
 * Теперь карточки раскладывает GridLayout, а здесь осталась только отрисовка.
 */
object BadgeAdapter {

    fun bind(b: ItemBadgeBinding, badge: Badge, position: Int) {
        val ctx = b.root.context

        b.badgeIcon.setImageResource(BadgeIcons.of(badge.code))
        b.badgeTitle.text = badge.title
        b.badgeDesc.text = badge.description

        if (badge.isUnlocked) {
            // фон карточки — colorSecondaryContainer, поэтому текст берём из темы,
            // иначе в светлой теме белым по светлому
            val onContainer = ThemeColors.onSecondaryContainer(ctx)
            b.badgeRoot.setBackgroundResource(R.drawable.bg_badge_unlocked)
            b.badgeIcon.alpha = 1f
            b.badgeIcon.imageTintList = ColorStateList.valueOf(onContainer)
            b.badgeTitle.setTextColor(onContainer)
            b.badgeDesc.setTextColor(onContainer)
            b.badgeDesc.alpha = 0.85f
            b.badgeStatus.text = "открыт " + TimeFmt.short(badge.unlockedAt ?: 0L)
            b.badgeStatus.setTextColor(onContainer)
            Anim.pop(b.badgeRoot, 30L * position)
        } else {
            b.badgeRoot.setBackgroundResource(R.drawable.bg_badge_locked)
            // закрытые приглушаем, но иконку оставляем видимой
            b.badgeIcon.alpha = 0.45f
            b.badgeIcon.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.on_bg_variant))
            b.badgeTitle.setTextColor(ContextCompat.getColor(ctx, R.color.on_bg))
            b.badgeDesc.setTextColor(ContextCompat.getColor(ctx, R.color.on_bg_variant))
            b.badgeDesc.alpha = 1f
            b.badgeStatus.text = "закрыт"
            b.badgeStatus.setTextColor(ContextCompat.getColor(ctx, R.color.on_bg_variant))
        }
        b.badgeRoot.visibility = View.VISIBLE
    }
}
