package com.eduardo.horarios.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.R
import com.eduardo.horarios.ui.components.TemplateList
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.ui.theme.headerBrush
import kotlinx.coroutines.launch

/**
 * Bienvenida en 3 pasos: qué es la app, activar avisos y elegir cómo empezar.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    DefaultStatusBarIcons()
    val context = LocalContext.current
    val app = context.applicationContext as HorariosApp
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        step = 2
    }

    fun finish() {
        app.settings.setOnboarded()
        onDone()
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            // Indicador de pasos
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (i == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "onboarding",
                modifier = Modifier.weight(1f),
            ) { s ->
                when (s) {
                    0 -> IntroStep(
                        emoji = "🗓️",
                        title = stringResource(R.string.ob_welcome_title),
                        text = stringResource(R.string.ob_welcome_text),
                        bullets = listOf(
                            stringResource(R.string.ob_bullet_1),
                            stringResource(R.string.ob_bullet_2),
                            stringResource(R.string.ob_bullet_3),
                        ),
                        primary = stringResource(R.string.ob_start),
                        onPrimary = { step = 1 },
                    )

                    1 -> IntroStep(
                        emoji = "🔔",
                        title = stringResource(R.string.ob_notif_title),
                        text = stringResource(R.string.ob_notif_text),
                        bullets = emptyList(),
                        primary = stringResource(R.string.ob_notif_enable),
                        onPrimary = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                step = 2
                            }
                        },
                        secondary = stringResource(R.string.ob_not_now),
                        onSecondary = { step = 2 },
                    )

                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 24.dp, bottom = 16.dp),
                    ) {
                        Text(stringResource(R.string.ob_template_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.ob_template_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        TemplateList(
                            onPick = { t ->
                                scope.launch {
                                    app.repository.createFromTemplate(t, activate = true)
                                    finish()
                                }
                            },
                            onBlank = {
                                scope.launch {
                                    app.repository.createSchedule(context.getString(R.string.default_schedule_name), "📅", 0)
                                    finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroStep(
    emoji: String,
    title: String,
    text: String,
    bullets: List<String>,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(headerBrush(MaterialTheme.colorScheme.primary)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 56.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (bullets.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bullets.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(b, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) { Text(primary, style = MaterialTheme.typography.titleMedium) }
        if (secondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 4.dp)) {
                Text(secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.height(52.dp))
        }
    }
}

