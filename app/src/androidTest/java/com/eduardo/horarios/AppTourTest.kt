package com.eduardo.horarios

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduardo.horarios.T.card
import com.eduardo.horarios.T.p
import com.eduardo.horarios.T.s
import com.eduardo.horarios.T.tab
import com.eduardo.horarios.data.ThemeMode
import com.eduardo.horarios.ui.home.DaySelection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Recorre la app como un usuario nuevo, en español (tema claro) y en inglés (tema oscuro):
 * bienvenida → plantilla → Hoy → opciones de una actividad → hecha → día libre →
 * actividad de un solo día → pegar lista → pestañas → diagnóstico.
 * Guarda una captura de cada pantalla.
 */
@RunWith(AndroidJUnit4::class)
class AppTourTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var prefix = ""

    private fun shot(name: String) = T.shot(compose, "${prefix}_$name")

    /** Español con tema claro. */
    @Test
    fun recorridoEspanolClaro() = recorrido(
        language = "es",
        dark = false,
        list = "10:00-11:00 Piano\n- Ir al súper\nLlamar a mamá 18:30",
        lastItem = "Llamar a mamá",
        oneOffTitle = "Dentista",
    )

    /** Inglés con tema oscuro. */
    @Test
    fun recorridoInglesOscuro() = recorrido(
        language = "en",
        dark = true,
        list = "10:00-11:00 Piano\n- Groceries\nCall mum 18:30",
        lastItem = "Call mum",
        oneOffTitle = "Dentist",
    )

    private fun recorrido(language: String, dark: Boolean, list: String, lastItem: String, oneOffTitle: String) {
        prefix = if (dark) "${language}_oscuro" else "${language}_claro"
        if (dark) T.onMain { T.app.settings.setThemeMode(ThemeMode.DARK) }
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)

        T.launch(compose, prefix, language = language) {
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

            // ---------- Progreso recién empezado: mensaje de ánimo, no un calendario gris ----------
            compose.waitFor(tab(s(R.string.tab_week)), timeoutMs = 30_000)
            compose.clickFirst(tab(s(R.string.tab_progress)))
            compose.waitFor(hasTestTag("consistency_empty"))
            compose.onNode(hasTestTag("consistency_empty")).performScrollTo()
            shot("03b_progreso_vacio")
            compose.clickFirst(tab(s(R.string.tab_today)))

            // ---------- Hoy (lunes de esta semana) ----------
            DaySelection.select(monday)
            val classes = s(R.string.tpl_act_classes)
            compose.waitFor(card(classes))
            compose.waitFor(card(s(R.string.tpl_act_study)))
            shot("04_hoy_lunes")

            // Solo las actividades que se siguen tienen círculo de «hecha»:
            // el lunes del estudiante son Clases y Comida (fijas) y Estudiar (se sigue) → 1 círculo
            val markDone = hasContentDescription(s(R.string.mark_done))
            assertEquals(1, compose.onAllNodes(markDone).fetchSemanticsNodes().size)

            // Opciones de una actividad fija → no hacerla solo este día (sin «Marcar como hecha»)
            compose.clickFirst(card(classes))
            val skipPrefix = s(R.string.skip_day, "").trim()
            compose.waitFor(hasText(skipPrefix, substring = true))
            assertEquals(0, compose.onAllNodes(hasText(s(R.string.mark_done))).fetchSemanticsNodes().size)
            shot("05_opciones_actividad")
            compose.clickFirst(hasText(skipPrefix, substring = true))
            compose.waitFor(hasText(s(R.string.skipped_this_day), substring = true))
            shot("06_actividad_saltada")

            // Marcar «Estudiar» como hecha con su círculo
            compose.clickFirst(markDone)
            compose.waitFor(hasContentDescription(s(R.string.mark_not_done)))

            // Día libre y deshacer
            compose.clickFirst(hasContentDescription(s(R.string.options)))
            compose.clickFirst(hasText(s(R.string.mark_day_off)))
            compose.waitFor(hasText(s(R.string.day_off_title)))
            shot("07_dia_libre")
            compose.clickFirst(hasText(s(R.string.undo)))
            compose.waitGone(hasText(s(R.string.day_off_title)))

            // ---------- Actividad de un solo día (el miércoles) ----------
            val wednesday = monday.plusDays(2)
            DaySelection.select(wednesday)
            compose.waitFor(card(classes))
            compose.clickFirst(hasTestTag("fab_add"))
            compose.waitFor(hasTestTag("repeat_once"))
            // Primero «Un solo día» y después el nombre: con el teclado abierto la pantalla se mueve
            compose.clickFirst(hasTestTag("repeat_once"))
            compose.waitFor(hasTestTag("date_box"))
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(oneOffTitle)
            Espresso.closeSoftKeyboard()
            compose.waitFor(hasText(longDate(T.localizedContext(), wednesday), substring = true))
            compose.onNode(hasTestTag("switch_tracked")).assertIsOn()
            shot("08_editor_un_solo_dia")
            compose.clickFirst(hasText(s(R.string.save)))
            compose.waitFor(card(oneOffTitle))
            shot("09_evento_miercoles")
            // La semana siguiente ya no aparece
            DaySelection.select(wednesday.plusDays(7))
            compose.waitFor(card(classes))
            compose.waitGone(card(oneOffTitle))

            // ---------- Domingo vacío → pegar una lista ----------
            DaySelection.select(monday.plusDays(6))
            compose.waitFor(hasText(s(R.string.bulk_add_link)))
            shot("10_dia_vacio")
            compose.clickFirst(hasText(s(R.string.bulk_add_link)))
            compose.waitFor(hasSetTextAction())
            compose.onNode(hasSetTextAction()).performTextInput(list)
            val addButton = hasText(p(R.plurals.bulk_add_button, 3))
            compose.waitFor(addButton)
            shot("11_pegar_lista")
            compose.clickFirst(addButton)
            compose.waitFor(card("Piano"))
            compose.waitFor(card(lastItem))
            shot("12_domingo_con_lista")

            // ---------- Pestañas ----------
            DaySelection.select(monday)
            compose.clickFirst(tab(s(R.string.tab_week)))
            compose.waitFor(hasText(s(R.string.this_week)))
            shot("13_semana")

            compose.clickFirst(tab(s(R.string.tab_schedules)))
            compose.waitFor(hasText(s(R.string.tpl_student), substring = true))
            shot("14_horarios")

            // ---------- Progreso ----------
            compose.clickFirst(tab(s(R.string.tab_progress)))
            compose.waitFor(hasText(s(R.string.stats_week_hours).uppercase()))
            compose.waitFor(hasTestTag("week_total"))
            shot("15_progreso")
            // «Estudiar» (lunes a jueves) se marcó el lunes: 1 de 4 esta semana, meta 3
            compose.waitFor(hasText(s(R.string.stats_habit_week, 1, 4, 3)))
            compose.waitFor(hasTestTag("heatmap"))
            compose.onNode(hasTestTag("heatmap")).performScrollTo()
            shot("15b_progreso_constancia")
            compose.onNode(hasTestTag("period_90")).performScrollTo().performClick()
            compose.onNode(hasTestTag("export_csv")).performScrollTo()
            shot("15c_progreso_habitos")

            compose.clickFirst(tab(s(R.string.tab_settings)))
            compose.waitFor(hasText(s(R.string.settings_about).uppercase()))
            shot("16_ajustes")

            // Resumen semanal: desactivado por defecto; se activa con su interruptor
            val weeklySwitch = hasAnyAncestor(hasTestTag("toggle_weekly_summary")) and isToggleable()
            compose.onNode(weeklySwitch).performScrollTo().assertIsOff().performClick()
            compose.onNode(weeklySwitch).assertIsOn()

            // ---------- Ajustes → Avanzado → Diagnóstico ----------
            compose.onNode(hasTestTag("open_diagnostics")).performScrollTo()
            shot("17_ajustes_avanzado")
            compose.onNode(hasTestTag("open_diagnostics")).performClick()
            compose.waitFor(hasText(s(R.string.diag_section_permissions).uppercase()))
            compose.waitGone(hasText(s(R.string.diag_loading)))
            shot("18_diagnostico")
            Espresso.pressBack()
            compose.waitFor(tab(s(R.string.tab_today)))
        }
    }
}
