package com.fastmask.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastmask.BuildConfig
import com.fastmask.R
import com.fastmask.ui.components.DesignCard
import com.fastmask.ui.components.DesignInput
import com.fastmask.ui.components.MonoEyebrow
import com.fastmask.ui.components.MonoLabel
import com.fastmask.ui.components.PillButton
import com.fastmask.ui.components.PillButtonVariant
import com.fastmask.ui.theme.FastMaskExtras
import com.fastmask.ui.theme.InstrumentSerif
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showToken by remember { mutableStateOf(false) }
    val extras = FastMaskExtras.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LoginEvent.LoginSuccess -> onLoginSuccess()
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 28.dp)
                .padding(top = 48.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            // Eyebrow
            MonoEyebrow(text = stringResource(R.string.login_eyebrow, BuildConfig.VERSION_NAME))
            Spacer(Modifier.height(14.dp))

            // Hero — A quiet place for [accent]masked mail[/accent].
            val heroPrefix = stringResource(R.string.login_hero_prefix)
            val heroAccent = stringResource(R.string.login_hero_accent)
            val heroSuffix = stringResource(R.string.login_hero_suffix)
            val annotated = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = InstrumentSerif,
                    ),
                ) { append(heroPrefix) }
                append(" ")
                withStyle(
                    SpanStyle(
                        color = extras.accent,
                        fontStyle = FontStyle.Italic,
                        fontFamily = InstrumentSerif,
                    ),
                ) { append(heroAccent) }
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = InstrumentSerif,
                    ),
                ) { append(heroSuffix) }
            }
            Text(
                text = annotated,
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.login_intro),
                color = extras.inkSoft,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(0.85f),
            )

            Spacer(Modifier.height(36.dp))

            // Token input
            DesignInput(
                value = uiState.token,
                onValueChange = viewModel::onTokenChange,
                label = stringResource(R.string.login_api_token_label),
                placeholder = stringResource(R.string.login_api_token_placeholder),
                mono = true,
                isError = uiState.error != null,
                enabled = !uiState.isLoading,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
                trailing = {
                    val icon = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    val cd = stringResource(if (showToken) R.string.login_hide_token else R.string.login_show_token)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(androidx.compose.ui.graphics.Color.Transparent)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = cd,
                            tint = extras.inkMuted,
                            modifier = Modifier
                                .size(20.dp),
                        )
                    }
                },
                hint = uiState.error,
            )

            Spacer(Modifier.height(16.dp))

            PillButton(
                text = if (uiState.isLoading) "…" else stringResource(R.string.login_button),
                onClick = { viewModel.login() },
                enabled = !uiState.isLoading && uiState.token.isNotBlank(),
                variant = PillButtonVariant.Primary,
                fullWidth = true,
                trailing = if (uiState.isLoading) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = extras.onAccent,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = extras.onAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(36.dp))

            // Instructions
            DesignCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                    MonoLabel(text = stringResource(R.string.login_instructions_title))
                    Spacer(Modifier.height(14.dp))
                    val steps = listOf(
                        stringResource(R.string.login_instructions_step1),
                        stringResource(R.string.login_instructions_step2),
                        stringResource(R.string.login_instructions_step3),
                        stringResource(R.string.login_instructions_step4),
                        stringResource(R.string.login_instructions_step5),
                    )
                    steps.forEachIndexed { index, step ->
                        InstructionRow(index = index + 1, text = step)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionRow(index: Int, text: String) {
    val extras = FastMaskExtras.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "%02d".format(index),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = extras.accent,
            modifier = Modifier
                .width(28.dp)
                .padding(top = 3.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = extras.inkSoft,
        )
    }
}
