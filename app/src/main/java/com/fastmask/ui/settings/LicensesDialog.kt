package com.fastmask.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastmask.R

/** One bundled third-party asset: its name, how it is licensed, and where the text lives. */
private class BundledLicense(
    val component: String,
    val license: String,
    val rawRes: Int,
)

// Proper names are not translated; the license texts are legal text and ship verbatim.
private val bundledLicenses = listOf(
    BundledLicense("Google Sans Flex (font)", "SIL Open Font License 1.1", R.raw.google_sans_flex_ofl),
    BundledLicense("Material Symbols \"alternate_email\" (launcher icon)", "Apache License 2.0", R.raw.apache_2_0),
)

/**
 * The license files are hard-wrapped at about 80 columns, which leaves ragged
 * half-lines in a narrower dialog. Joins lines inside a paragraph (blank lines stay)
 * and shortens the rule lines; the wording is untouched.
 */
private fun reflow(text: String): String =
    text.replace(Regex("(?<=\\S)[ \\t]*\\r?\\n[ \\t]*(?=\\S)"), " ")
        .replace(Regex("-{5,}"), "———")

/**
 * Attribution for what FastMask bundles: the font (SIL OFL 1.1) and the icon glyph
 * (Apache 2.0). Both licenses require their text to accompany the software.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LicensesDialog(onDismiss: () -> Unit) {
    val resources = LocalContext.current.resources
    val texts = remember {
        bundledLicenses.map { license ->
            reflow(resources.openRawResource(license.rawRes).bufferedReader().use { it.readText() })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licenses_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                bundledLicenses.forEachIndexed { index, license ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(license.component, style = MaterialTheme.typography.titleSmall)
                        Text(
                            license.license,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            texts[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.pro_close))
            }
        },
    )
}
