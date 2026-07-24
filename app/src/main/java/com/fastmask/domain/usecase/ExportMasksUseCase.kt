package com.fastmask.domain.usecase

import com.fastmask.domain.model.MaskedEmail
import com.fastmask.domain.repository.MaskedEmailRepository
import javax.inject.Inject

/**
 * Builds a CSV snapshot of all masks (Pro feature). Pure string output — file
 * writing and the share sheet are UI-layer concerns.
 */
class ExportMasksUseCase @Inject constructor(
    private val repository: MaskedEmailRepository,
) {
    suspend operator fun invoke(): Result<String> =
        repository.getMaskedEmails().map { masks -> MaskCsv.build(masks) }
}

object MaskCsv {

    private val HEADER = listOf(
        "email", "state", "for_domain", "description", "url", "created_at", "last_message_at",
    )

    fun build(masks: List<MaskedEmail>): String = buildString {
        appendLine(HEADER.joinToString(","))
        masks.forEach { mask ->
            appendLine(
                listOf(
                    mask.email,
                    mask.state.name.lowercase(),
                    mask.forDomain.orEmpty(),
                    mask.description.orEmpty(),
                    mask.url.orEmpty(),
                    mask.createdAt?.toString().orEmpty(),
                    mask.lastMessageAt?.toString().orEmpty(),
                ).joinToString(",") { it.csvEscaped() }
            )
        }
    }

    /**
     * Characters that make spreadsheets execute a cell as a formula. CR/LF are
     * included per OWASP: some parsers strip leading control characters and
     * then evaluate the remainder, so `\r=HYPERLINK(...)` must also be
     * neutralized, not just quoted.
     */
    private val FORMULA_LEAD_CHARS = setOf('=', '+', '-', '@', '\t', '\r', '\n')

    /**
     * RFC 4180 quoting plus spreadsheet formula neutralization: a mask note
     * like `=HYPERLINK(...)` must open as text, not execute, in Excel/Sheets
     * (OWASP CSV-injection mitigation — leading apostrophe).
     */
    private fun String.csvEscaped(): String {
        // Inspect the first NON-whitespace char: some spreadsheet importers trim
        // leading whitespace before evaluating a cell, so `" =HYPERLINK(...)"`
        // would otherwise slip past a first-char-only check and execute.
        val neutralized = if (trimStart().firstOrNull() in FORMULA_LEAD_CHARS) "'$this" else this
        return if (neutralized.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${neutralized.replace("\"", "\"\"")}\""
        } else {
            neutralized
        }
    }
}
