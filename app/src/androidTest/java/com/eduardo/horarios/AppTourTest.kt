package com.eduardo.horarios

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduardo.horarios.T.card
import com.eduardo.horarios.T.p
import com.eduardo.horarios.T.s
import com.eduardo.horarios.T.tab
import com.eduardo.horarios.ui.home.DaySelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Recorre la app como un usuario nuevo (en español y tema claro):
 * bienvenida → plantilla → Hoy → opciones de una actividad → día libre → pegar lista → pestañas.
 * Guarda una captura de cada pantalla.
 */
@RunWith(AndroidJUnit4::class)
class AppTourTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun shot(name: String) = T.shot(compose, name)

    @Test
    fun recorridoCompleto() {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)

        T.launch(compose, "recorrido", language = "es") {
            // ---------- Bienvenida ----------
            compose.waitFor(hasText(s(R.string.ob_welcome_title)))
            shot("01_bienvenida")
            compose.clickFirst(hasText(s(R.string.ob_start)))

            compose.waitFor(hasText(s(R.string.ob_notif_title)))
            shot("02_bienvenida_avisos")
            compose.clickFirst(hasText(s(R.string.ob_not_now)))

            compose.waitFor(hasText(s(R.string.ob_template_title)))
            shot("03_bienvenida_plantillas")
            compose.clickFirst(hasText(s(R.string.tpl_student)))

            // ---------- Hoy (lunes de esta semana) ----------
            compose.waitFor(tab(s(R.string.tab_week)), timeoutMs = 30_000)
            DaySelection.select(monday)
            val classes = s(R.string.tpl_act_classes)
            compose.waitFor(card(classes))
            shot("04_hoy_lunes")

            // Opciones de una actividad → no hacerla solo este día
            compose.clickFirst(card(classes))
            val skipPrefix = s(R.string.skip_day, "").trim()
            compose.waitFor(hasText(skipPrefix, substring = true))
            shot("05_opciones_actividad")
            compose.clickFirst(hasText(skipPrefix, substring = true))
            compose.waitFor(hasText(s(R.string.skipped_this_day), substring = true))
            shot("06_actividad_saltada")

            // Marcar la comida como hecha
            compose.clickFirst(card(s(R.string.tpl_act_lunch)))
            compose.clickFirst(hasText(s(R.string.mark_done)))
            compose.waitGone(hasText(s(R.string.mark_done)))

            // Día libre y deshacer
            compose.clickFirst(hasContentDescription(s(R.string.options)))
            compose.clickFirst(hasText(s(R.string.mark_day_off)))
            compose.waitFor(hasText(s(R.string.day_off_title)))
            shot("07_dia_libre")
            compose.clickFirst(hasText(s(R.string.undo)))
            compose.waitGone(hasText(s(R.string.day_off_title)))

            // ---------- Domingo vacío → pegar una lista ----------
            DaySelection.select(monday.plusDays(6))
            compose.waitFor(hasText(s(R.string.bulk_add_link)))
            shot("08_dia_vacio")
            compose.clickFirst(hasText(s(R.string.bulk_add_link)))
            compose.waitFor(hasSetTextAction())
            compose.onNode(hasSetTextAction()).performTextInput("10:00-11:00 Piano\n- Ir al súper\nLlamar a mamá 18:30")
            val addButton = hasText(p(R.plurals.bulk_add_button, 3))
            compose.waitFor(addButton)
            shot("09_pegar_lista")
            compose.clickFirst(addButton)
            compose.waitFor(card("Piano"))
            compose.waitFor(card("Llamar a mamá"))
            shot("10_domingo_con_lista")

            // ---------- Pestañas ----------
            DaySelection.select(monday)
            compose.clickFirst(tab(s(R.string.tab_week)))
            compose.waitFor(hasText(s(R.string.this_week)))
            shot("11_semana")

            compose.clickFirst(tab(s(R.string.tab_schedules)))
            compose.waitFor(hasText(s(R.string.tpl_student), substring = true))
            shot("12_horarios")

            compose.clickFirst(tab(s(R.string.tab_progress)))
            compose.waitFor(hasText(s(R.string.stats_this_week).uppercase()))
            shot("13_progreso")

            compose.clickFirst(tab(s(R.string.tab_settings)))
            compose.waitFor(hasText(s(R.string.settings_about).uppercase()))
            shot("14_ajustes")

            // ---------- Editor ----------
            compose.clickFirst(tab(s(R.string.tab_today)))
            compose.clickFirst(hasText(s(R.string.add)) and androidx.compose.ui.test.hasClickAction())
            compose.waitFor(hasContentDescription(s(R.string.bulk_add_title)))
            shot("15_editor_nueva_actividad")
        }
    }
}
