package com.eduardo.horarios

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduardo.horarios.T.s
import com.eduardo.horarios.T.tab
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.BackupFormat
import com.eduardo.horarios.data.HorariosFile
import com.eduardo.horarios.data.WeekParity
import com.eduardo.horarios.pro.LockReason
import com.eduardo.horarios.pro.Paywall
import com.eduardo.horarios.pro.Pro
import com.eduardo.horarios.pro.ProRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Horarios Pro: qué es gratis, qué se bloquea y qué pasa al terminar la suscripción.
 * Se prueba con la variante GitHub forzando «sin Pro» / «con Pro» (la compra real solo existe en Play).
 */
@RunWith(AndroidJUnit4::class)
class ProFlowsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val repo get() = T.app.repository

    private fun setPro(active: Boolean) = runBlocking {
        Pro.setForTests(active)
        repo.enforceFreeLimits()
    }

    private fun activity(scheduleId: Long, title: String, parity: Int = 0, reminder: Int = 10) = ActivityEntity(
        scheduleId = scheduleId, title = title, emoji = "📚", daysMask = 0b1111111,
        startMinute = 23 * 60, endMinute = 23 * 60 + 30, colorIndex = 0, reminderMinutes = reminder,
        weekParity = parity,
    )

    /** Espera a que se abra la hoja de Pro con ese motivo y la cierra con «Ahora no». */
    private fun expectPaywall(reasonRes: Int, shot: String? = null) {
        compose.waitFor(hasTestTag("paywall"))
        compose.waitFor(hasText(s(reasonRes)))
        // En la variante GitHub no hay tienda: el botón de compra está desactivado y lo explica
        compose.waitFor(hasText(s(R.string.pro_store_unavailable)))
        compose.onNode(hasTestTag("paywall_buy")).assertIsNotEnabled()
        shot?.let { T.shot(compose, it) }
        compose.onNode(hasText(s(R.string.pro_not_now))).performScrollTo().performClick()
        compose.waitGone(hasTestTag("paywall"))
    }

    @Test
    fun gratisHastaDosHorarios() {
        setPro(false)
        T.onMain { T.app.settings.setOnboarded() }
        runBlocking {
            assertNotNull(repo.createSchedule("Clases", "📚", 0))
            assertNotNull(repo.createSchedule("Vacaciones", "🏖️", 2))
            // El tercero no se deja crear sin Pro (ni duplicar)
            assertNull(repo.createSchedule("Exámenes", "📝", 3))
            assertFalse(repo.duplicateSchedule(repo.getActiveSchedule()!!))
            assertFalse(repo.canCreateSchedule())
        }
        T.launch(compose, "pro_limite", language = "es") {
            compose.clickFirst(tab(s(R.string.tab_schedules)))
            compose.clickFirst(hasTestTag("new_schedule"))
            expectPaywall(R.string.pro_r_schedules, "pro_1_limite_horarios")
            // Con Pro, «Nuevo horario» abre las plantillas
            setPro(true)
            compose.clickFirst(hasTestTag("new_schedule"))
            compose.waitFor(hasText(s(R.string.template_pick_text)))
            Espresso.pressBack()
        }
        runBlocking { assertTrue(repo.canCreateSchedule()) }
    }

    @Test
    fun funcionesProConCandado() {
        setPro(false)
        T.onMain { T.app.settings.setOnboarded() }
        runBlocking { repo.createSchedule("Clases", "📚", 0) }
        T.launch(compose, "pro_candados", language = "es") {
            // ---------- Editor: semanas alternas y turnos ----------
            compose.selectDay(java.time.LocalDate.now())
            compose.clickFirst(hasTestTag("fab_add"))
            compose.waitFor(hasText(s(R.string.repeat_alternate) + " 🔒"))
            T.shot(compose, "pro_2_editor_candados")
            compose.clickFirst(hasTestTag("repeat_alternate"))
            expectPaywall(R.string.pro_r_alternate, "pro_3_hoja_pro")
            compose.onNode(hasTestTag("repeat_weekly")).assertIsSelected()
            compose.clickFirst(hasTestTag("repeat_rotation"))
            expectPaywall(R.string.pro_r_rotation)
            compose.onNode(hasTestTag("repeat_weekly")).assertIsSelected()
            Espresso.pressBack()

            // ---------- Horarios: fechas automáticas ----------
            compose.clickFirst(tab(s(R.string.tab_schedules)))
            val id = runBlocking { repo.getActiveSchedule()!!.id }
            compose.clickFirst(hasTestTag("schedule_menu_$id"))
            compose.clickFirst(hasTestTag("menu_auto_dates"))
            expectPaywall(R.string.pro_r_dates)

            // ---------- Progreso: 90 días, constancia y CSV ----------
            compose.clickFirst(tab(s(R.string.tab_progress)))
            compose.onNode(hasTestTag("period_90")).performScrollTo().performClick()
            expectPaywall(R.string.pro_r_stats)
            compose.onNode(hasTestTag("period_28")).performScrollTo().performClick()
            compose.onNode(hasTestTag("export_csv")).performScrollTo().performClick()
            expectPaywall(R.string.pro_r_stats)

            // ---------- Ajustes: colores y copia en carpeta ----------
            compose.clickFirst(tab(s(R.string.tab_settings)))
            // En la variante GitHub no hay tarjeta de suscripción
            compose.waitGone(hasTestTag("pro_card"))
            val locked = "accent_${ProRules.FREE_ACCENTS}"
            compose.onNode(hasTestTag(locked)).performScrollTo().performClick()
            expectPaywall(R.string.pro_r_colors)
            assertEquals(0, T.app.settings.accent.value)
            compose.onNode(hasTestTag("accent_1")).performScrollTo().performClick()
            compose.waitUntil(5_000) { T.app.settings.accent.value == 1 }
            compose.onNode(hasText(s(R.string.auto_backup_folder_pick) + " 🔒")).performScrollTo().performClick()
            expectPaywall(R.string.pro_r_folder)
        }
        // Y por si alguien se lo salta: el repositorio tampoco guarda semanas alternas sin Pro
        runBlocking {
            val sid = repo.getActiveSchedule()!!.id
            assertFalse(repo.saveActivity(activity(sid, "Pádel", parity = WeekParity.B)))
            assertTrue(repo.saveActivity(activity(sid, "Piano")))
            repo.setAutoRange(sid, 20000, 20010)
            assertNull(repo.getActiveSchedule()!!.autoFrom)
        }
    }

    @Test
    fun alTerminarProSeEligenDosParaSiempre() {
        setPro(true)
        T.onMain { T.app.settings.setOnboarded() }
        val ids = runBlocking {
            val a = repo.createSchedule("Aula", "📚", 0)!!
            val b = repo.createSchedule("Biblio", "📖", 1)!!
            val c = repo.createSchedule("Campo", "⚽", 2)!!
            val d = repo.createSchedule("Dojo", "🥋", 3)!!
            val e = repo.createSchedule("Custodia", "👨‍👩‍👧", 4)!!
            repo.saveActivity(activity(a, "Repasar"))
            repo.saveActivity(activity(d, "Kárate"))
            // «Custodia» usa semanas alternas (Pro)
            assertTrue(repo.saveActivity(activity(e, "Con papá", parity = WeekParity.A)))
            assertTrue(repo.activate(d))
            listOf(a, b, c, d, e)
        }
        val (a, b, c, d, e) = ids
        // Termina la suscripción
        setPro(false)
        runBlocking {
            val st = repo.currentFreeState()
            assertTrue(st.needsChoice)
            assertEquals(listOf(a, b, c, d), st.choosable.map { it.id })
            assertEquals(LockReason.PRO_FEATURE, st.locked[e])
        }
        T.launch(compose, "pro_fin", language = "es") {
            compose.waitFor(hasText(s(R.string.pro_choice_title)))
            T.shot(compose, "pro_4_elegir_horarios")
            compose.onNode(hasTestTag("keep_confirm")).assertIsNotEnabled()
            compose.clickFirst(hasTestTag("keep_$a"))
            compose.clickFirst(hasTestTag("keep_$b"))
            // Un tercero no se puede marcar
            compose.clickFirst(hasTestTag("keep_$c"))
            compose.onNode(hasTestTag("keep_confirm")).assertIsEnabled().performClick()
            compose.waitFor(hasText(s(R.string.pro_choice_confirm_title)))
            compose.waitFor(hasText("📚 Aula, 📖 Biblio", substring = true))
            T.shot(compose, "pro_5_confirmar")
            compose.clickFirst(hasTestTag("keep_final"))
            compose.waitGone(hasText(s(R.string.pro_choice_title)))

            // Horarios: los no elegidos y el de semanas alternas, con candado
            compose.clickFirst(tab(s(R.string.tab_schedules)))
            // (el texto del candado forma parte de la tarjeta, que es pulsable)
            compose.waitFor(hasText("Campo", substring = true) and hasText(s(R.string.lock_over_limit), substring = true))
            compose.waitFor(hasText("Dojo", substring = true) and hasText(s(R.string.lock_over_limit), substring = true))
            compose.waitFor(hasText("Custodia", substring = true) and hasText(s(R.string.lock_pro_feature), substring = true))
            compose.waitFor(hasText("Aula", substring = true) and hasText(s(R.string.active_badge), substring = true))
            T.shot(compose, "pro_6_horarios_bloqueados")
            // Tocar uno bloqueado explica que vuelve con Pro
            compose.onNode(hasText("Dojo", substring = true) and androidx.compose.ui.test.hasClickAction()).performClick()
            expectPaywall(R.string.pro_r_locked)
            // Su menú solo deja desbloquear con Pro o borrar
            compose.clickFirst(hasTestTag("schedule_menu_$c"))
            compose.waitFor(hasTestTag("menu_unlock"))
            compose.waitGone(hasText(s(R.string.edit)))
            Espresso.pressBack()
        }
        runBlocking {
            val st = repo.currentFreeState()
            assertEquals(setOf(c, d, e), st.locked.keys)
            assertEquals(LockReason.OVER_LIMIT, st.locked[c])
            assertFalse(st.needsChoice)
            // «Dojo» estaba activo y ha quedado bloqueado: ahora manda el más antiguo de los libres
            assertEquals(a, repo.getActiveSchedule()!!.id)
            assertFalse(repo.activate(d))
            assertEquals(a, repo.getActiveSchedule()!!.id)
            // Los avisos solo son del horario activo (no del bloqueado)
            val next = T.app.scheduler.nextReminder()
            assertNotNull(next)
            assertEquals(a, next!!.activity.scheduleId)
            // No se ha borrado nada
            assertEquals(5, T.app.database.scheduleDao().getAll().size)
            assertEquals(1, T.app.database.activityDao().getForSchedule(e).size)

            // Borrar uno de los libres deja crear otro nuevo, pero no desbloquea los otros
            repo.deleteSchedule(T.app.database.scheduleDao().getAll().first { it.id == b })
            assertTrue(repo.canCreateSchedule())
            val f = repo.createSchedule("Nuevo", "✨", 5)
            assertNotNull(f)
            val after = repo.currentFreeState()
            assertEquals(setOf(c, d, e), after.locked.keys)
            assertFalse(after.needsChoice)
            assertFalse(repo.keepSchedules(setOf(c, d)))
        }
        // Al volver a Pro todo vuelve tal cual
        setPro(true)
        runBlocking {
            val st = repo.currentFreeState()
            assertTrue(st.locked.isEmpty())
            assertTrue(T.app.database.scheduleDao().getAll().none { it.freeLocked })
            assertTrue(repo.activate(e))
        }
    }

    @Test
    fun importarSinProNoSaltaElLimite() {
        setPro(false)
        T.onMain { T.app.settings.setOnboarded() }
        runBlocking {
            repo.createSchedule("Uno", "1️⃣", 0)
            val two = repo.createSchedule("Dos", "2️⃣", 1)!!
            repo.saveActivity(activity(two, "Algo"))

            // Un horario compartido que llega con el límite ya cubierto: bloqueado
            val shared = HorariosFile(HorariosFile.KIND_SCHEDULE, listOf(repo.exportSchedule(repo.getActiveSchedule()!!, withDone = false)))
            repo.import(shared, replace = false)
            val st = repo.currentFreeState()
            assertEquals(1, st.locked.size)
            assertEquals(LockReason.OVER_LIMIT, st.locked.values.single())
            assertFalse(st.needsChoice)

            // Una copia hecha con Pro (3 horarios sin bloquear) restaurada sin Pro: el tercero llega bloqueado
            val threeFree = BackupFormat.parse(
                BackupFormat.toJson(
                    HorariosFile.KIND_BACKUP,
                    repo.exportAll().map { it.copy(freeLocked = false) },
                )
            )
            assertEquals(3, threeFree.schedules.size)
            repo.import(threeFree, replace = true)
            val restored = repo.currentFreeState()
            assertEquals(1, restored.locked.size)
            assertFalse(restored.needsChoice)

            // Y la copia guarda los bloqueos: restaurarla no desbloquea nada
            val json = BackupFormat.toJson(HorariosFile.KIND_BACKUP, repo.exportAll())
            assertTrue(json.contains("\"freeLocked\": true"))
            repo.import(BackupFormat.parse(json), replace = true)
            assertEquals(1, repo.currentFreeState().locked.size)
            assertEquals(1, T.app.database.scheduleDao().getAll().count { it.freeLocked })
        }
    }

    /** Pro se acaba con la app abierta; desde el aviso se vuelve a Pro y todo sigue igual. */
    @Test
    fun seAcabaProConLaAppAbiertaYSeRenueva() {
        setPro(true)
        T.onMain { T.app.settings.setOnboarded() }
        runBlocking {
            repo.createSchedule("Uno", "1️⃣", 0)
            repo.createSchedule("Dos", "2️⃣", 1)
            repo.createSchedule("Tres", "3️⃣", 2)
        }
        T.launch(compose, "pro_renovar", language = "es") {
            compose.waitFor(tab(s(R.string.tab_today)))
            // Termina la suscripción mientras se usa la app
            setPro(false)
            compose.waitFor(hasText(s(R.string.pro_choice_title)))
            // «Volver a Pro» abre la hoja de Pro encima del aviso
            compose.clickFirst(hasText(s(R.string.pro_back_to_pro)))
            compose.waitFor(hasTestTag("paywall"))
            compose.waitFor(hasText(s(R.string.pro_r_locked)))
            T.shot(compose, "pro_8_volver_a_pro")
            // Se renueva: la hoja y el aviso se cierran solos
            setPro(true)
            compose.waitGone(hasTestTag("paywall"))
            compose.waitGone(hasText(s(R.string.pro_choice_title)))
        }
        runBlocking {
            val st = repo.currentFreeState()
            assertTrue(st.locked.isEmpty())
            assertFalse(st.needsChoice)
            assertEquals(3, T.app.database.scheduleDao().getAll().size)
        }
    }

    @Test
    fun hojaProEnIngles() {
        setPro(false)
        T.onMain { T.app.settings.setOnboarded() }
        runBlocking { repo.createSchedule("Work", "💼", 1) }
        T.onMain { T.app.settings.setThemeMode(com.eduardo.horarios.data.ThemeMode.DARK) }
        T.launch(compose, "pro_en", language = "en") {
            Paywall.show(com.eduardo.horarios.pro.PaywallReason.GENERAL)
            compose.waitFor(hasTestTag("paywall"))
            compose.waitFor(hasText(s(R.string.pro_b_alternate)))
            T.shot(compose, "pro_7_hoja_pro_ingles")
            compose.onNode(hasText(s(R.string.pro_not_now))).performScrollTo().performClick()
            compose.waitGone(hasTestTag("paywall"))
        }
    }
}
