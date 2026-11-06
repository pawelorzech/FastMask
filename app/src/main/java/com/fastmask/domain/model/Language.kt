package com.fastmask.domain.model

enum class Language(val code: String) {
    ENGLISH("en"),
    CHINESE("zh-Hans"),
    SPANISH("es"),
    HINDI("hi"),
    ARABIC("ar"),
    PORTUGUESE("pt"),
    BENGALI("bn"),
    RUSSIAN("ru"),
    JAPANESE("ja"),
    FRENCH("fr"),
    GERMAN("de"),
    KOREAN("ko"),
    ITALIAN("it"),
    TURKISH("tr"),
    VIETNAMESE("vi"),
    POLISH("pl"),
    UKRAINIAN("uk"),
    DUTCH("nl"),
    THAI("th"),
    INDONESIAN("id");

    companion object {
        fun fromCode(code: String?): Language? {
            return entries.find { it.code == code }
        }
    }
}
