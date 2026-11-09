package com.fastmask.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fastmask.R
import com.fastmask.domain.model.MaskedEmail
import com.fastmask.ui.accessibility.screenHeading
import com.fastmask.ui.components.expressive.StateChip
import com.fastmask.ui.components.expressive.StateMorphAvatar

@Composable
internal fun Hero(email: MaskedEmail) {
    val local = email.localPart
    val domainPart = email.domainPart

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val domainStyle = MaterialTheme.typography.titleMedium

    // One Text whose string is exactly the address, laid out as two paragraphs
    // (a paragraph style starts a new line without adding a character).
    val address = remember(email.email, onSurface, onSurfaceVariant, domainStyle) {
        buildAnnotatedString {
            withStyle(ParagraphStyle(lineBreak = LineBreak.Simple)) {
                withStyle(SpanStyle(color = onSurface)) { append(local) }
            }
            if (domainPart.isNotEmpty()) {
                withStyle(ParagraphStyle(lineHeight = 28.sp, lineBreak = LineBreak.Simple)) {
                    withStyle(
                        SpanStyle(
                            color = onSurfaceVariant,
                            fontSize = domainStyle.fontSize,
                            fontWeight = domainStyle.fontWeight,
                        ),
                    ) { append("@$domainPart") }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StateMorphAvatar(
            initial = email.avatarSeed,
            state = email.state,
            size = 88.dp,
        )
        SelectionContainer {
            Text(
                text = address,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                modifier = Modifier.screenHeading(),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateChip(state = email.state)
            Text(
                text = email.forDomain?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.email_detail_no_domain),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
