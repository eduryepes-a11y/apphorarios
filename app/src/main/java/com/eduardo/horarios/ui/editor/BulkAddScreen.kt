@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.eduardo.horarios.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.R
import com.eduardo.horarios.data.ListParser
import com.eduardo.horarios.hm
import com.eduardo.horarios.ui.components.SectionLabel
import com.eduardo.horarios.ui.theme.DefaultStatusBarIcons
import com.eduardo.horarios.ui.theme.paletteColor
import kotlinx.coroutines.launch

/** Pegar o escribir una lista y crear varias actividades de golpe. */
@Composable
fun BulkAddScreen(initialDay: Int, onBack: () -> Unit) {
    DefaultStatusBarIcons()
    val context = LocalContext.current
    val app = context.applicationContext as HorariosApp
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var mask by rememberSaveable { mutableIntStateOf(1 shl initialDay.coerceIn(0, 6)) }
    val accent = MaterialTheme.colorScheme.primary

    val items = remember(text) { ListParser.parse(text) }
    val preview = remember(items, mask) { ListParser.toActivities(items, mask) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bulk_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = {
                        scope.launch {
                            app.repository.addActivities(preview)
                            Toast.makeText(
                                context,
                                context.resources.getQuantityString(R.plurals.bulk_added, preview.size, preview.size),
                                Toast.LENGTH_SHORT,
                            ).show()
                            onBack()
                        }
                    },
                    enabled = preview.isNotEmpty() && mask != 0,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp),
                ) {
                    Text(
                        if (preview.isEmpty()) stringResource(R.string.bulk_add_button_empty)
                        else pluralStringResource(R.plurals.bulk_add_button, preview.size, preview.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.bulk_add_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(3000) },
                placeholder = { Text(stringResource(R.string.bulk_add_placeholder)) },
                minLines = 6,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                SectionLabel(stringResource(R.string.days))
                DaysPicker(mask = mask, accent = accent) { mask = it }
            }
            if (preview.isNotEmpty()) {
                Column {
                    SectionLabel(stringResource(R.string.bulk_add_preview))
                    preview.forEach { a ->
                        Row(
                            Modifier.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${hm(a.startMinute)}–${hm(a.endMinute)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = paletteColor(a.colorIndex),
                                modifier = Modifier.width(96.dp),
                            )
                            Text(a.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(a.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
