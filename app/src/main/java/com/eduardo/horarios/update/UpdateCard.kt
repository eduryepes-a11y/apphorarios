package com.eduardo.horarios.update

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardo.horarios.R
import androidx.compose.ui.res.stringResource
import com.eduardo.horarios.ui.theme.headerBrush
import kotlinx.coroutines.launch

/** Tarjeta de "Nueva versión disponible" para la pantalla principal. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by UpdateManager.state.collectAsStateWithLifecycle()

    // Comprobación silenciosa al abrir la app
    LaunchedEffect(Unit) { UpdateManager.check(context.applicationContext, silent = true) }

    val visible = state is UpdateState.Available || state is UpdateState.Downloading ||
        state is UpdateState.ReadyToInstall || (state is UpdateState.Error && (state as UpdateState.Error).info != null)

    AnimatedVisibility(visible, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(headerBrush(MaterialTheme.colorScheme.primary))
                .padding(18.dp),
        ) {
            when (val s = state) {
                is UpdateState.Available -> {
                    Text(stringResource(R.string.update_new_version, s.info.versionName), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { installOrAskPermission(context, s.info, scope) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                        ) { Text(stringResource(R.string.update_button) + sizeLabel(s.info.sizeBytes)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { UpdateManager.dismiss() }) {
                            Text(stringResource(R.string.later), color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                is UpdateState.Downloading -> {
                    Text(stringResource(R.string.update_downloading, s.info.versionName), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${(s.progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }

                is UpdateState.ReadyToInstall -> {
                    Text(stringResource(R.string.update_ready_title, s.info.versionName), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.update_ready_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { installOrAskPermission(context, s.info, scope) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                        ) { Text(stringResource(R.string.update_install)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { UpdateManager.dismiss() }) {
                            Text(stringResource(R.string.later), color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                is UpdateState.Error -> {
                    Text("⚠️ " + stringResource(s.messageRes) + (s.detail?.let { ": $it" } ?: ""), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.info?.let { info ->
                            Button(
                                onClick = { installOrAskPermission(context, info, scope) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                            ) { Text(stringResource(R.string.retry)) }
                        }
                        TextButton(onClick = { UpdateManager.dismiss() }) {
                            Text(stringResource(R.string.close), color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                else -> Box {}
            }
        }
    }
}

/** Si falta el permiso de «instalar apps desconocidas», abre Ajustes; si no, descarga/instala. */
private fun installOrAskPermission(context: Context, info: UpdateInfo, scope: CoroutineScope) {
    if (!UpdateManager.canInstall(context)) {
        Toast.makeText(context, context.getString(R.string.update_allow_source), Toast.LENGTH_LONG).show()
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        )
    } else {
        scope.launch { UpdateManager.install(context.applicationContext, info) }
    }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes <= 0) "" else " · %.1f MB".format(bytes / 1_048_576.0)
