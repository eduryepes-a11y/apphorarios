@file:OptIn(ExperimentalMaterial3Api::class)

package com.eduardo.horarios.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardo.horarios.HorariosApp
import com.eduardo.horarios.R
import com.eduardo.horarios.pro.FreeState
import com.eduardo.horarios.pro.Paywall
import com.eduardo.horarios.pro.PaywallReason
import com.eduardo.horarios.pro.Pro
import com.eduardo.horarios.pro.ProBilling
import com.eduardo.horarios.pro.ProRules
import com.eduardo.horarios.pro.StoreStatus
import kotlinx.coroutines.launch

/** Lo que incluye Pro, en el orden en que se muestra. */
private val PRO_BENEFITS = listOf(
    "🗂️" to R.string.pro_b_schedules,
    "🔁" to R.string.pro_b_alternate,
    "🏥" to R.string.pro_b_rotation,
    "📅" to R.string.pro_b_dates,
    "☁️" to R.string.pro_b_folder,
    "📊" to R.string.pro_b_stats,
    "🎨" to R.string.pro_b_colors,
)

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Hoja «Horarios Pro»: se abre con [Paywall.show] desde cualquier pantalla. */
@Composable
fun PaywallHost() {
    // collectAsState (sin esperar al ciclo de vida): la hoja puede abrirse encima de otro diálogo
    val reason by Paywall.open.collectAsState()
    val r = reason ?: return
    val context = LocalContext.current
    val pro by Pro.state.collectAsState()
    val offer by ProBilling.offer.collectAsStateWithLifecycle()
    val status by ProBilling.status.collectAsStateWithLifecycle()
    // Si se acaba de comprar, se cierra sola
    LaunchedEffect(pro.active) { if (pro.active) Paywall.close() }
    // Al abrirla, se vuelve a preguntar a Google Play por el precio (por si se cortó la conexión)
    LaunchedEffect(Unit) { ProBilling.refresh() }
    if (pro.active) return
    ModalBottomSheet(
        onDismissRequest = { Paywall.close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .testTag("paywall"),
        ) {
            Text("✨", fontSize = 40.sp)
            Text(
                stringResource(R.string.pro_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(reasonText(r)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp).testTag("paywall_reason"),
            )
            Spacer(Modifier.height(16.dp))
            for ((emoji, text) in PRO_BENEFITS) {
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 16.sp) }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                stringResource(R.string.pro_free_stays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(20.dp))
            val o = offer
            // Condiciones bien visibles antes del botón: precio, prueba y renovación automática
            Text(
                when {
                    o != null && o.trialDays > 0 -> stringResource(R.string.pro_price_after_trial, o.trialDays, o.price)
                    o != null -> stringResource(R.string.pro_price_monthly, o.price)
                    status == StoreStatus.CONNECTING -> stringResource(R.string.pro_connecting)
                    else -> stringResource(R.string.pro_store_unavailable)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("paywall_price"),
            )
            val canBuy = ProBilling.canPurchase && o != null && status == StoreStatus.READY
            Button(
                onClick = {
                    val activity = context.findActivity()
                    if (activity == null || !ProBilling.launchPurchase(activity)) ProBilling.refresh()
                },
                enabled = canBuy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("paywall_buy"),
            ) {
                Text(
                    when {
                        o == null -> stringResource(R.string.pro_buy)
                        o.trialDays > 0 -> stringResource(R.string.pro_try_free, o.trialDays)
                        else -> stringResource(R.string.pro_subscribe, o.price)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            TextButton(onClick = { Paywall.close() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.pro_not_now))
            }
        }
    }
}

private fun reasonText(r: PaywallReason): Int = when (r) {
    PaywallReason.SCHEDULES -> R.string.pro_r_schedules
    PaywallReason.ALTERNATE -> R.string.pro_r_alternate
    PaywallReason.ROTATION -> R.string.pro_r_rotation
    PaywallReason.AUTO_DATES -> R.string.pro_r_dates
    PaywallReason.BACKUP_FOLDER -> R.string.pro_r_folder
    PaywallReason.STATS -> R.string.pro_r_stats
    PaywallReason.COLORS -> R.string.pro_r_colors
    PaywallReason.LOCKED_SCHEDULE -> R.string.pro_r_locked
    PaywallReason.GENERAL -> R.string.pro_r_general
}

/**
 * Al terminar Pro con más de 2 horarios normales: hay que elegir cuáles se quedan (no se puede
 * cerrar sin elegir o volver a Pro). La elección es definitiva.
 */
@Composable
fun FreeChoiceHandler() {
    val context = LocalContext.current
    val app = context.applicationContext as HorariosApp
    val state by app.repository.freeState.collectAsStateWithLifecycle(
        initialValue = FreeState(true, emptyMap(), needsChoice = false, choosable = emptyList(), canCreate = true),
    )
    if (!state.known || !state.needsChoice) return
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirming by remember { mutableStateOf(false) }
    // Mientras se guarda la elección no se muestra ningún diálogo (para que no vuelva a asomar el primero)
    var submitting by remember { mutableStateOf(false) }
    if (submitting) return

    if (!confirming) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.pro_choice_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.pro_choice_text, ProRules.FREE_SCHEDULES),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    for (s in state.choosable) {
                        val checked = s.id in selected
                        val full = selected.size >= ProRules.FREE_SCHEDULES
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = checked || !full) {
                                    selected = if (checked) selected - s.id else selected + s.id
                                }
                                .padding(vertical = 2.dp)
                                .testTag("keep_${s.id}"),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = checked || !full)
                            Spacer(Modifier.width(6.dp))
                            Text("${s.emoji} ${s.name}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirming = true },
                    enabled = selected.size == ProRules.FREE_SCHEDULES,
                    modifier = Modifier.testTag("keep_confirm"),
                ) { Text(stringResource(R.string.pro_choice_keep)) }
            },
            dismissButton = {
                Row {
                    // «Ya pago»: vuelve a preguntar a Google Play (por si fue un error de conexión)
                    if (ProBilling.canPurchase) {
                        TextButton(onClick = { ProBilling.refresh() }, modifier = Modifier.testTag("restore_purchase")) {
                            Text(stringResource(R.string.pro_restore))
                        }
                    }
                    TextButton(onClick = { Paywall.show(PaywallReason.LOCKED_SCHEDULE) }) {
                        Text(stringResource(R.string.pro_back_to_pro))
                    }
                }
            },
        )
    } else {
        val names = state.choosable.filter { it.id in selected }.joinToString(", ") { "${it.emoji} ${it.name}" }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.pro_choice_confirm_title)) },
            text = { Text(stringResource(R.string.pro_choice_confirm_text, names)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val keep = selected
                        submitting = true
                        app.appScope.launch {
                            val ok = app.repository.keepSchedules(keep)
                            if (!ok) {
                                // No debería pasar; si pasa, se vuelve a pedir
                                submitting = false
                                confirming = false
                                selected = emptySet()
                            }
                        }
                    },
                    modifier = Modifier.testTag("keep_final"),
                ) { Text(stringResource(R.string.pro_choice_final)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Abre la página de Google Play para gestionar la suscripción. */
fun openManageSubscription(context: Context) {
    val url = ProBilling.manageUrl(context) ?: return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Etiqueta pequeña «PRO» para lo que está bloqueado. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** Separación entre elementos (para listas horizontales con insignia). */
val ProBadgeSpacing = Arrangement.spacedBy(6.dp)
