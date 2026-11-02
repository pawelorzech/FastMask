package com.fastmask.ui.util

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Relative times must read naturally in every locale. Until 1.13.2 they were
 * hand-written abbreviations and most translations kept the English unit letters
 * ("vor 5mo", "hace 5mo", "il y a 5mo"); Italian and Portuguese used "m" for both
 * minutes and months.
 */
@RunWith(AndroidJUnit4::class)
class RelativeTimeLocaleTest {

    private val now = 1_800_000_000L
    private fun ago(seconds: Long) = Instant.ofEpochSecond(now - seconds)
    private val fiveMonths = 5L * 31 * 24 * 3600
    private val fiveMinutes = 5L * 60

    private fun context(tag: String): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    private fun label(tag: String, secondsAgo: Long) = RelativeTime.format(context(tag), ago(secondsAgo), now)

    @Test
    fun monthsUseTheLocalesOwnWord() {
        val expected = mapOf("de" to "Mon", "fr" to "m.", "es" to "m", "it" to "mes", "pt-BR" to "mes", "tr" to "ay", "nl" to "maand", "pl" to "mies", "ru" to "мес", "en" to "mo")
        expected.forEach { (tag, word) ->
            val text = label(tag, fiveMonths)
            assertTrue("$tag: '$text' should contain '$word'", text.contains(word, ignoreCase = true))
            assertTrue("$tag: '$text' should contain the count", text.contains("5"))
        }
    }

    @Test
    fun noLocaleKeepsTheEnglishMonthAbbreviation() {
        listOf("de", "fr", "es", "bn", "hi", "nl", "tr", "it", "pt-BR", "id", "vi", "uk").forEach { tag ->
            val text = label(tag, fiveMonths)
            assertFalse("$tag still says '$text'", Regex("""\d\s*mo\b""").containsMatchIn(text))
        }
    }

    @Test
    fun minutesAndMonthsNeverLookTheSame() {
        listOf("it", "pt-BR", "en", "de", "fr", "es").forEach { tag ->
            val minutes = label(tag, fiveMinutes)
            val months = label(tag, fiveMonths)
            assertFalse("$tag: minutes '$minutes' and months '$months' must differ", minutes == months)
        }
    }
}
