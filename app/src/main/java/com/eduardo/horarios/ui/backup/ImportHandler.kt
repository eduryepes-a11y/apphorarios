@file:OptIn(ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.backup

import androidx.compose.ui.ExperimentalComposeUiApi
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.R
import com.eduardo.horarios.data.HorariosFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Muestra el diálogo de importar cuando llega un archivo de Horarios. */
@Composable
fun ImportHandler() {
    val context = LocalContext.current
    val app = context.applicationContext as HorariosApp
    val pendingUri by ImportController.pending.collectAsState()
    var file by remember { mutableStateOf<HorariosFile?>(null) }
    var invalid by remember { mutableStateOf(false) }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        try {
            file = BackupIO.readFile(context, uri)
        } catch (e: Exception) {
            invalid = true
        }
        ImportController.pending.value = null
    }

    fun doImport(f: HorariosFile, replace: Boolean) {
        file = null
        app.appScope.launch {
            val n = app.repository.import(f, replace)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.import_done, n, n),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    file?.let { f ->
        if (f.isBackup) {
            AlertDialog(
                onDismissRequest = { file = null },
                title = { Text(stringResource(R.string.import_backup_title)) },
                text = {
                    Text(
                        pluralStringResource(R.plurals.import_backup_text, f.schedules.size, f.schedules.size)
                    )
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { doImport(f, replace = false) }) { Text(stringResource(R.string.import_add)) }
                        TextButton(onClick = { doImport(f, replace = true) }) {
                            Text(stringResource(R.string.import_replace), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = { TextButton(onClick = { file = null }) { Text(stringResource(R.string.cancel)) } },
            )
        } else {
            val s = f.schedules.first()
            AlertDialog(
                onDismissRequest = { file = null },
                title = { Text(stringResource(R.string.import_schedule_title, "${s.emoji} ${s.name}")) },
                text = {
                    Text(pluralStringResource(R.plurals.import_schedule_text, s.activities.size, s.activities.size))
                },
                confirmButton = {
                    TextButton(onClick = { doImport(f, replace = false) }) { Text(stringResource(R.string.import_button)) }
                },
                dismissButton = { TextButton(onClick = { file = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    if (invalid) {
        AlertDialog(
            onDismissRequest = { invalid = false },
            title = { Text(stringResource(R.string.import_invalid_title)) },
            text = { Text(stringResource(R.string.import_invalid_text)) },
            confirmButton = { TextButton(onClick = { invalid = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
