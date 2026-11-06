package com.fastmask.ui.settings

import com.fastmask.R
import com.fastmask.domain.model.Language

/** Endonym string resource for the language picker; the name is not translated. */
internal val Language.displayNameRes: Int
    get() = when (this) {
        Language.ENGLISH -> R.string.language_en
        Language.CHINESE -> R.string.language_zh
        Language.SPANISH -> R.string.language_es
        Language.HINDI -> R.string.language_hi
        Language.ARABIC -> R.string.language_ar
        Language.PORTUGUESE -> R.string.language_pt
        Language.BENGALI -> R.string.language_bn
        Language.RUSSIAN -> R.string.language_ru
        Language.JAPANESE -> R.string.language_ja
        Language.FRENCH -> R.string.language_fr
        Language.GERMAN -> R.string.language_de
        Language.KOREAN -> R.string.language_ko
        Language.ITALIAN -> R.string.language_it
        Language.TURKISH -> R.string.language_tr
        Language.VIETNAMESE -> R.string.language_vi
        Language.POLISH -> R.string.language_pl
        Language.UKRAINIAN -> R.string.language_uk
        Language.DUTCH -> R.string.language_nl
        Language.THAI -> R.string.language_th
        Language.INDONESIAN -> R.string.language_id
    }
