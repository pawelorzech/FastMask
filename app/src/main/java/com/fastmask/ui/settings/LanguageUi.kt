package com.fastmask.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fastmask.R
import com.fastmask.domain.model.Language
import androidx.compose.foundation.selection.selectableGroup
import com.fastmask.ui.components.DialogTextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

private const val CLOSE_AFTER_SELECT_MS = 220L

@Composable
internal fun LanguagePickerDialog(
    selected: Language?,
    onSelect: (Language?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember { mutableStateOf(selected) }
    var committed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val choose: (Language?) -> Unit = { language ->
        if (!committed) {
            committed = true
            pending = language
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            scope.launch {
                delay(CLOSE_AFTER_SELECT_MS)
                onSelect(language)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_select_language)) },
        text = {
            // heightIn, not height: at a 200% font scale a fixed box can
            // overflow the dialog on a short screen in landscape.
            Box(modifier = Modifier.heightIn(max = 360.dp)) {
                LazyColumn(modifier = Modifier.selectableGroup()) {
                    item {
                        LanguageRow(
                            label = stringResource(R.string.settings_system_default),
                            isSelected = pending == null,
                            onClick = { choose(null) },
                        )
                    }
                    items(items = Language.entries.toList(), key = { it.code }) { lang ->
                        LanguageRow(
                            label = stringResource(lang.displayNameRes),
                            isSelected = pending == lang,
                            onClick = { choose(lang) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogTextButton(
                text = stringResource(R.string.email_detail_delete_cancel),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun LanguageRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // TalkBack must announce which language is active, not just a list
            // of identically-labelled buttons.
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
