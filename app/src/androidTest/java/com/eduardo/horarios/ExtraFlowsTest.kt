package com.eduardo.horarios

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduardo.horarios.T.card
import com.eduardo.horarios.T.s
import com.eduardo.horarios.data.ActivityEntity
import com.eduardo.horarios.data.BackupFormat
import com.eduardo.horarios.data.HorariosFile
import com.eduardo.horarios.data.StatsCalculator
import com.eduardo.horarios.data.Templates
import com.eduardo.horarios.data.WeekParity
import com.eduardo.horarios.data.WeekCalibration
import com.eduardo.horarios.data.AutoBackup
import com.eduardo.horarios.data.AutoBackupFiles
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertNotNull
import java.io.File
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.eduardo.horarios.ui.home.DaySelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** «Voy con retraso», exportar a CSV y copia de seguridad (ida y vuelta). */
@RunWith(AndroidJUnit4::class)
class ExtraFlowsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val repo get() = T.app.repository

    /** Crea un horario vacío y activo. */
    private fun newSchedule(): Long = runBlocking {
        T.onMain { T.app.settings.setOnboarded() }
        repo.createSchedule("Prueba", "🧪", 0)
        repo.getActiveSchedule()!!.id
    }

    @Test
    fun vasConRetraso() {
        val now = nowMinuteOfDay()
        // Necesitamos una actividad que empiece dentro de un rato y termine hoy
        assumeTrue("Demasiado tarde para la prueba", now < 21 * 60)
        val scheduleId = newSchedule()
        val start = ((now + 60) / 5) * 5
        val today = LocalDate.now()
        runBlocking {
            repo.saveActivity(
                ActivityEntity(
                    scheduleId = scheduleId, title = "Piano", emoji = "🎹",
                    daysMask = 1 shl (today.dayOfWeek.value - 1),
                    startMinute = start, endMinute = start + 60, colorIndex = 1, reminderMinutes = 10,
                )
            )
        }
        T.launch(compose, "retraso", language = "es") {
            compose.waitFor(card("Piano"))
            compose.clickFirst(hasText(s(R.string.running_late)))
            compose.waitFor(hasText(s(R.string.delay_title)))
            T.shot(compose, "retraso_1_opciones")
            val quarter = durationLabel(T.localizedContext(), 15)
            compose.clickFirst(hasText("+$quarter"))
            // El chip de la cabecera cambia y la actividad se mueve 15 minutos
            compose.waitFor(hasText(s(R.string.delayed_by, quarter)))
            compose.waitFor(hasText(hm(start + 15)))
            T.shot(compose, "retraso_2_movida")
        }
        // Y queda guardado para hoy
        val o = runBlocking { repo.statsSnapshot(today.toEpochDay(), today.toEpochDay())!!.third }
        assertEquals(15, o.sumOf { it.minutes })
    }

    @Test
    fun exportarCsv() = runBlocking {
        T.onMain { T.app.settings.setOnboarded() }
        repo.createFromTemplate(Templates.first { it.key == "office" }, activate = true)
        val today = LocalDate.now()
        val acts = repo.getActiveActivities()
        val todays = acts.firstOrNull { it.daysMask and (1 shl (today.dayOfWeek.value - 1)) != 0 }
        if (todays != null) repo.setDone(todays.id, today.toEpochDay(), true)

        val from = today.minusDays(364)
        val (a, c, o) = repo.statsSnapshot(from.toEpochDay(), today.toEpochDay())!!
        val csv = StatsCalculator.csv(from, today, a, c, o)
        val lines = csv.trim().lines()
        assertEquals("date,activity,start,end,delay_min,status,tracked", lines.first())
        assertTrue("debería haber muchas filas", lines.size > 100)
        if (todays != null) assertTrue(lines.any { it.startsWith("$today,") && it.contains(",done,") })
    }

    @Test
    fun copiaDeSeguridadIdaYVuelta() = runBlocking {
        T.onMain { T.app.settings.setOnboarded() }
        repo.createFromTemplate(Templates.first { it.key == "student" }, activate = true)
        val scheduleId = repo.getActiveSchedule()!!.id
        val dentistDay = LocalDate.now().plusDays(3)
        repo.saveActivity(
            ActivityEntity(
                scheduleId = scheduleId, title = "Dentista", emoji = "🦷",
                daysMask = 1 shl (dentistDay.dayOfWeek.value - 1), startMinute = 17 * 60, endMinute = 18 * 60,
                colorIndex = 2, reminderMinutes = 30, onDate = dentistDay.toEpochDay(), tracked = false,
            )
        )
        repo.saveActivity(
            ActivityEntity(
                scheduleId = scheduleId, title = "Pádel", emoji = "🎾", daysMask = 0b0010000,
                startMinute = 19 * 60, endMinute = 20 * 60, colorIndex = 3, reminderMinutes = 10, weekParity = WeekParity.B,
            )
        )
        repo.saveActivity(
            ActivityEntity(
                scheduleId = scheduleId, title = "Turno", emoji = "🏥", daysMask = 0b1111111,
                startMinute = 7 * 60, endMinute = 15 * 60, colorIndex = 4, reminderMinutes = 30,
                weekParity = 0, rotStart = dentistDay.toEpochDay(), rotOn = 4, rotOff = 3,
            )
        )
        repo.saveActivity(
            ActivityEntity(
                scheduleId = scheduleId, title = "Inglés", emoji = "🇬🇧", daysMask = 0b0001000,
                startMinute = 18 * 60, endMinute = 19 * 60, colorIndex = 5, reminderMinutes = 10,
                weekParity = 3, weekCycle = 4,
            )
        )
        // Letras ajustadas: en el ciclo de 3, esta semana pasa a ser la siguiente letra
        val target = WeekParity.of(LocalDate.now(), 3) % 3 + 1
        repo.setThisWeekLetter(3, target)
        val offsets = WeekParity.offsets.value
        assertTrue(offsets.isNotEmpty())
        assertEquals(target, WeekParity.of(LocalDate.now(), 3))
        val from = LocalDate.now().plusDays(30).toEpochDay()
        repo.setAutoRange(scheduleId, from, from + 6)
        val before = repo.getActiveActivities()

        // Exportar → texto → leer → importar reemplazando todo
        val json = BackupFormat.toJson(HorariosFile.KIND_BACKUP, repo.exportAll())
        val parsed = BackupFormat.parse(json)
        assertTrue(parsed.isBackup)
        assertEquals(offsets, parsed.weekOffsets)
        // Se pierde el ajuste… y la copia lo recupera
        T.onMain { WeekCalibration.replaceAll(T.app, emptyMap()) }
        assertTrue(WeekParity.offsets.value.isEmpty())
        repo.import(parsed, replace = true)

        val after = repo.getActiveActivities()
        assertEquals(before.size, after.size)
        val dentist = after.single { it.title == "Dentista" }
        assertEquals(dentistDay.toEpochDay(), dentist.onDate)
        assertFalse(dentist.tracked)
        // v1.5: semanas alternas y fechas automáticas
        assertEquals(WeekParity.B, after.single { it.title == "Pádel" }.weekParity)
        val turno = after.single { it.title == "Turno" }
        assertEquals(dentistDay.toEpochDay(), turno.rotStart)
        assertEquals(4, turno.rotOn)
        assertEquals(3, turno.rotOff)
        val ingles = after.single { it.title == "Inglés" }
        assertEquals(3, ingles.weekParity)
        assertEquals(4, ingles.weekCycle)
        assertEquals(offsets, WeekParity.offsets.value)
        assertEquals(target, WeekParity.of(LocalDate.now(), 3))
        val restored = repo.getActiveSchedule()!!
        assertEquals(from, restored.autoFrom)
        assertEquals(from + 6, restored.autoTo)
        // Las fijas siguen sin contar en Progreso tras la copia
        assertFalse(after.first { it.title == s(R.string.tpl_act_lunch) }.tracked)
    }

    /** Horario con fechas: se activa solo, vuelve el de siempre al terminar y se ve en la tarjeta. */
    @Test
    fun horarioPorFechas() {
        val today = LocalDate.now().toEpochDay()
        val (normalId, holidaysId) = runBlocking {
            T.onMain { T.app.settings.setOnboarded() }
            val n = repo.createSchedule("Clases", "📚", 0)
            val h = repo.createSchedule("Vacaciones", "🏖️", 2)
            n to h
        }
        runBlocking {
            assertEquals(normalId, repo.getActiveSchedule()!!.id)
            // Unas fechas que ya cubren hoy: se activa en el momento
            repo.setAutoRange(holidaysId, today - 1, today + 5)
            assertEquals(holidaysId, repo.getActiveSchedule()!!.id)
            // El mismo día no vuelve a cambiar nada
            assertFalse(repo.runAutoSwitch(today))
        }
        T.launch(compose, "fechas", language = "es") {
            compose.clickFirst(T.tab(s(R.string.tab_schedules)))
            // La etiqueta se funde con la tarjeta (pulsable), así que se busca por su texto
            val range = "📅 " + shortRange(T.localizedContext(), LocalDate.ofEpochDay(today - 1), LocalDate.ofEpochDay(today + 5))
            compose.waitFor(hasText(range, substring = true))
            T.shot(compose, "fechas_1_tarjeta")
            compose.clickFirst(hasTestTag("schedule_menu_$holidaysId"))
            compose.clickFirst(hasTestTag("menu_auto_dates"))
            compose.waitFor(hasTestTag("auto_dates_text"))
            // La cabecera muestra las fechas guardadas, cortas
            compose.waitFor(hasTestTag("auto_dates_headline") and hasText(range.removePrefix("📅 ")))
            T.shot(compose, "fechas_2_dialogo")
            compose.clickFirst(hasText(s(R.string.cancel)))
            compose.waitGone(hasTestTag("auto_dates_text"))
        }
        runBlocking {
            // Pasado el periodo vuelve «Clases» solo
            assertTrue(repo.runAutoSwitch(today + 6))
            assertEquals(normalId, repo.getActiveSchedule()!!.id)
            // Quitar las fechas no cambia nada (ya está «Clases»)
            repo.setAutoRange(holidaysId, null, null)
            assertEquals(normalId, repo.getActiveSchedule()!!.id)
            // Unas fechas nuevas que cubren hoy lo activan; al quitarlas vuelve el de siempre
            repo.setAutoRange(holidaysId, today, today + 2)
            assertEquals(holidaysId, repo.getActiveSchedule()!!.id)
            repo.setAutoRange(holidaysId, null, null)
            assertEquals(normalId, repo.getActiveSchedule()!!.id)
        }
    }

    /** Copia automática: se crea, se limpian las viejas y se restaura desde Ajustes. */
    @Test
    fun copiaAutomaticaYRestaurar() {
        val context = T.app
        runBlocking {
            T.onMain { T.app.settings.setOnboarded() }
            repo.createFromTemplate(Templates.first { it.key == "student" }, activate = true)
        }
        val count = runBlocking { repo.getActiveActivities().size }
        assertTrue(AutoBackup.isEnabled(context))
        // Copias viejas de relleno: solo deben quedar las 7 más recientes
        val dir = AutoBackup.dir(context).apply { mkdirs() }
        val today = LocalDate.now()
        (1L..9L).forEach { File(dir, AutoBackupFiles.nameFor(today.minusDays(it))).writeText("{}") }
        val file = runBlocking { AutoBackup.runIfDue(context, repo, force = true) }
        assertNotNull(file)
        val names = AutoBackup.list(context).map { it.name }
        assertEquals(AutoBackupFiles.KEEP, names.size)
        assertEquals(AutoBackupFiles.nameFor(today), names.first())
        // Sin forzar, el mismo día no se repite
        assertEquals(null, runBlocking { AutoBackup.runIfDue(context, repo) })

        // Se borra una actividad… y se recupera restaurando la copia de hoy
        runBlocking { repo.deleteActivity(repo.getActiveActivities().first()) }
        assertEquals(count - 1, runBlocking { repo.getActiveActivities().size })
        T.launch(compose, "copia_auto", language = "es") {
            compose.clickFirst(T.tab(s(R.string.tab_settings)))
            compose.onNode(hasTestTag("restore_auto")).performScrollTo()
            compose.waitFor(hasText(s(R.string.auto_backup_last, s(R.string.today))))
            // Sin carpeta elegida, el consejo de Drive («Mi unidad») está a la vista
            compose.waitFor(hasTestTag("auto_backup_folder_hint"))
            T.shot(compose, "copia_auto_1_ajustes")
            compose.onNode(hasTestTag("restore_auto")).performClick()
            compose.waitFor(hasTestTag("auto_copy_0"))
            T.shot(compose, "copia_auto_2_lista")
            compose.clickFirst(hasTestTag("auto_copy_0"))
            compose.clickFirst(hasTestTag("confirm_restore"))
            compose.waitUntil(10_000) { runBlocking { repo.getActiveActivities().size } == count }
        }
    }

    /** Turnos por días (2 sí, 2 no) y semanas alternas con ciclo de 3, creadas desde el editor. */
    @Test
    fun turnosYCiclosDeSemanas() {
        newSchedule()
        val today = LocalDate.now()
        T.launch(compose, "turnos", language = "es") {
            compose.selectDay(today)
            // ---------- Turnos: 2 días sí, 2 no, empezando hoy ----------
            compose.clickFirst(hasTestTag("fab_add"))
            compose.clickFirst(hasTestTag("repeat_rotation"))
            compose.waitFor(hasTestTag("rot_on_value"))
            repeat(2) { compose.clickFirst(hasTestTag("rot_on_minus")) }
            repeat(2) { compose.clickFirst(hasTestTag("rot_off_minus")) }
            compose.waitFor(hasTestTag("rotation_hint") and hasText(s(R.string.rotation_hint, 2, 2, 4)))
            compose.waitFor(hasText(longDate(T.localizedContext(), today), substring = true))
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Guardia")
            Espresso.closeSoftKeyboard()
            T.shot(compose, "turnos_1_editor")
            compose.clickFirst(hasText(s(R.string.save)))
            compose.waitFor(card("Guardia"))
            compose.selectDay(today.plusDays(1))
            compose.waitFor(card("Guardia"))
            compose.selectDay(today.plusDays(2))
            compose.waitGone(card("Guardia"))
            compose.selectDay(today.plusDays(4))
            compose.waitFor(card("Guardia"))

            // ---------- Semanas alternas con ciclo de 3 (esta semana) ----------
            compose.selectDay(today)
            compose.clickFirst(hasTestTag("fab_add"))
            compose.clickFirst(hasTestTag("repeat_alternate"))
            compose.clickFirst(hasTestTag("cycle_3"))
            compose.waitFor(hasTestTag("week_c"))
            val letter = WeekParity.letter(WeekParity.of(today, 3))
            compose.onNode(hasTestTag("week_${letter.lowercase()}")).assertIsSelected()
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Noches")
            Espresso.closeSoftKeyboard()
            T.shot(compose, "turnos_2_ciclo_3")
            compose.clickFirst(hasText(s(R.string.save)))
            compose.waitFor(card("Noches"))
            // La cabecera dice qué semana del ciclo es
            compose.waitFor(hasText(s(R.string.week_label, letter), substring = true))
            compose.selectDay(today.plusWeeks(1))
            compose.waitGone(card("Noches"))
            compose.selectDay(today.plusWeeks(3))
            compose.waitFor(card("Noches"))
            T.shot(compose, "turnos_3_tres_semanas_despues")

            // ---------- «Esta semana es la…»: ajustar las letras a las de la vida real ----------
            compose.selectDay(today)
            compose.waitFor(card("Noches"))
            compose.clickFirst(hasTestTag("fab_add"))
            compose.clickFirst(hasTestTag("repeat_alternate"))
            compose.clickFirst(hasTestTag("cycle_3"))
            // Queda por debajo del botón «Guardar»: primero hay que desplazarse hasta él
            compose.onNode(hasTestTag("change_week_letter")).performScrollTo().performClick()
            compose.waitFor(hasText(s(R.string.week_change_title)))
            T.shot(compose, "turnos_4_que_semana_es")
            // Otra letra distinta de la actual (la siguiente del ciclo)
            val newLetter = WeekParity.letter(WeekParity.of(today, 3) % 3 + 1)
            compose.clickFirst(hasTestTag("this_week_${newLetter.lowercase()}"))
            compose.waitGone(hasText(s(R.string.week_change_title)))
            compose.waitFor(hasTestTag("week_parity_hint") and hasText(s(R.string.week_cycle_hint, newLetter, "A, B, C")))
            assertEquals(newLetter, WeekParity.letter(WeekParity.of(today, 3)))
            T.shot(compose, "turnos_5_letra_cambiada")
            Espresso.pressBack()
            // «Noches» era de la semana «letter»: ahora esta semana es otra, así que hoy ya no está…
            compose.waitGone(card("Noches"))
            compose.waitFor(hasText(s(R.string.week_label, newLetter), substring = true))
            // …y está en la semana que ahora tiene su letra
            val nochesWeek = (1L..2L).first { WeekParity.letter(WeekParity.of(today.plusWeeks(it), 3)) == letter }
            compose.selectDay(today.plusWeeks(nochesWeek))
            compose.waitFor(card("Noches"))
            T.shot(compose, "turnos_6_noches_en_su_semana")
        }
    }
}
