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
        // Las fijas siguen sin contar en Progreso tras la copia
        assertFalse(after.first { it.title == s(R.string.tpl_act_lunch) }.tracked)
    }
}
