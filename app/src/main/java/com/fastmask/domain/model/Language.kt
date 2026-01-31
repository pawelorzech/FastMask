package com.fastmask.domain.model

import com.fastmask.R

enum class Language(
    val code: String,
    val displayNameRes: Int
) {
    ENGLISH("en", R.string.language_en),
    CHINESE("zh", R.string.language_zh),
    SPANISH("es", R.string.language_es),
    HINDI("hi", R.string.language_hi),
    ARABIC("ar", R.string.language_ar),
    PORTUGUESE("pt", R.string.language_pt),
    BENGALI("bn", R.string.language_bn),
    RUSSIAN("ru", R.string.language_ru),
    JAPANESE("ja", R.string.language_ja),
    FRENCH("fr", R.string.language_fr),
    GERMAN("de", R.string.language_de),
    KOREAN("ko", R.string.language_ko),
    ITALIAN("it", R.string.language_it),
    TURKISH("tr", R.string.language_tr),
    VIETNAMESE("vi", R.string.language_vi),
    POLISH("pl", R.string.language_pl),
    UKRAINIAN("uk", R.string.language_uk),
    DUTCH("nl", R.string.language_nl),
    THAI("th", R.string.language_th),
    INDONESIAN("id", R.string.language_id);

    companion object {
        fun fromCode(code: String?): Language? {
            return entries.find { it.code == code }
        }
    }
}
