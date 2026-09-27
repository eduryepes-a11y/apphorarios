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
import com.eduardo.horarios.data.AutoBackup
import com.eduardo.horarios.data.AutoBackupFiles
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertNotNull
import java.io.File
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
        val from = LocalDate.now().plusDays(30).toEpochDay()
        repo.setAutoRange(scheduleId, from, from + 6)
        val before = repo.getActiveActivities()

        // Exportar → texto → leer → importar reemplazando todo
        val json = BackupFormat.toJson(HorariosFile.KIND_BACKUP, repo.exportAll())
        val parsed = BackupFormat.parse(json)
        assertTrue(parsed.isBackup)
        repo.import(parsed, replace = true)

        val after = repo.getActiveActivities()
        assertEquals(before.size, after.size)
        val dentist = after.single { it.title == "Dentista" }
        assertEquals(dentistDay.toEpochDay(), dentist.onDate)
        assertFalse(dentist.tracked)
        // v1.5: semanas alternas y fechas automáticas
        assertEquals(WeekParity.B, after.single { it.title == "Pádel" }.weekParity)
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
            // Y al quitar unas fechas que cubren hoy también se vuelve al de siempre
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
            T.shot(compose, "copia_auto_1_ajustes")
            compose.onNode(hasTestTag("restore_auto")).performClick()
            compose.waitFor(hasTestTag("auto_copy_0"))
            T.shot(compose, "copia_auto_2_lista")
            compose.clickFirst(hasTestTag("auto_copy_0"))
            compose.clickFirst(hasTestTag("confirm_restore"))
            compose.waitUntil(10_000) { runBlocking { repo.getActiveActivities().size } == count }
        }
    }
}
