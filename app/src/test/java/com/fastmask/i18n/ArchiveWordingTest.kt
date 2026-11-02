package com.fastmask.i18n

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In FastMask "delete" is archive: the mask is restorable. English says "Archived" and
 * every locale's `list_archived_snackbar` says "archived", but 18 locales once said
 * "Deleted" for the filter, state and detail dialog — the dialog even claimed the
 * action could not be undone. This pins the wording per locale with a small word list
 * (lower-cased substrings), so a translation drifting back to "delete" fails here.
 */
class ArchiveWordingTest {

    private val resDir = File("src/main/res")

    /** Keys whose English text is a form of "Archive" but whose key still says "delete". */
    private val archiveKeys = listOf(
        "filter_deleted",
        "state_deleted",
        "email_detail_deleted",
        "email_detail_delete",
        "email_detail_delete_dialog_title",
        "email_detail_delete_confirm",
    )

    /** The dialog body explains restore/bounce; it need not say "archive", but must not say delete. */
    private val deleteFreeKeys = listOf("email_detail_delete_dialog_message")

    private class Words(val archive: List<String>, val delete: List<String>)

    private val words = mapOf(
        "ar" to Words(listOf("أرشف", "مؤرشف", "الأرشفة"), listOf("حذ")),
        "b+zh+Hans" to Words(listOf("存档", "归档"), listOf("删除")),
        "bn" to Words(listOf("আর্কাইভ"), listOf("মুছ")),
        "de" to Words(listOf("archiv"), listOf("lösch", "gelösch")),
        "es" to Words(listOf("archiv"), listOf("elimin")),
        "fr" to Words(listOf("archiv"), listOf("supprim")),
        "hi" to Words(listOf("संग्रह", "आर्काइव"), listOf("हटा")),
        "id" to Words(listOf("arsip"), listOf("hapus")),
        "it" to Words(listOf("archiv"), listOf("elimin")),
        "ja" to Words(listOf("アーカイブ"), listOf("削除")),
        "ko" to Words(listOf("보관"), listOf("삭제")),
        "nl" to Words(listOf("archiv"), listOf("verwijder")),
        "pl" to Words(listOf("archiw"), listOf("usu")),
        "pt" to Words(listOf("arquiv"), listOf("exclu")),
        "ru" to Words(listOf("архив"), listOf("удал")),
        "th" to Words(listOf("เก็บ"), listOf("ลบ")),
        "tr" to Words(listOf("arşiv"), listOf("sil")),
        "uk" to Words(listOf("архів"), listOf("видал")),
        "vi" to Words(listOf("lưu trữ"), listOf("xóa")),
    )

    private fun strings(locale: String): Map<String, String> {
        val file = File(resDir, "values-$locale/strings.xml")
        val xml = file.readText()
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].lowercase() }
    }

    @Test
    fun everyLocaleHasAWordList() {
        val locales = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .map { it.name.removePrefix("values-") }
            .filter { it !in setOf("night", "v31", "night-v31") }
            .toSet()
        assertTrue("locales without a word list: ${locales - words.keys}", words.keys.containsAll(locales))
    }

    @Test
    fun archiveWordingIsNotTranslatedAsDelete() {
        val problems = mutableListOf<String>()
        for ((locale, w) in words) {
            val s = strings(locale)
            val snackbar = s["list_archived_snackbar"].orEmpty()
            if (w.archive.none { snackbar.contains(it) }) {
                problems += "$locale: list_archived_snackbar '$snackbar' has no archive root (fix the word list)"
                continue
            }
            for (key in deleteFreeKeys) {
                val text = s[key] ?: continue
                if (w.delete.any { text.contains(it) }) problems += "$locale: $key says delete: '$text'"
            }
            for (key in archiveKeys) {
                val text = s[key] ?: continue
                if (w.delete.any { text.contains(it) }) problems += "$locale: $key says delete: '$text'"
                if (w.archive.none { text.contains(it) }) problems += "$locale: $key lacks an archive word: '$text'"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
