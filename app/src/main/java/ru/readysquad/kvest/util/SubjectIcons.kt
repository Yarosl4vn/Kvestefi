package ru.readysquad.kvest.util

import androidx.annotation.DrawableRes
import ru.readysquad.kvest.R

/** Свои иконки предметов: сопоставление кода предмета и векторного ресурса. */
object SubjectIcons {

    @DrawableRes
    fun of(subjectCode: String): Int = when (subjectCode) {
        "math" -> R.drawable.ic_subj_math
        "rus" -> R.drawable.ic_subj_rus
        "hist" -> R.drawable.ic_subj_hist
        "phys" -> R.drawable.ic_subj_phys
        "chem" -> R.drawable.ic_subj_chem
        "bio" -> R.drawable.ic_subj_bio
        "geo" -> R.drawable.ic_subj_geo
        "social" -> R.drawable.ic_subj_social
        "info" -> R.drawable.ic_subj_info
        "eng" -> R.drawable.ic_subj_eng
        "lit" -> R.drawable.ic_subj_lit
        else -> R.drawable.ic_subj_math
    }
}
