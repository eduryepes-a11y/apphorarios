package com.eduardo.horarios

import android.Manifest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.eduardo.horarios.T.card
import com.eduardo.horarios.T.s
import com.eduardo.horarios.T.tab
import com.eduardo.horarios.data.Templates
import com.eduardo.horarios.data.ThemeMode
import com.eduardo.horarios.ui.home.DaySelection
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/** La app ya configurada, en inglés y tema oscuro, con permisos concedidos. */
@RunWith(AndroidJUnit4::class)
class DarkEnglishTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private fun shot(name: String) = T.shot(compose, name)

    @Test
    fun oscuroEnIngles() {
        T.onMain {
            T.app.settings.setLanguage("en")
            T.app.settings.setThemeMode(ThemeMode.DARK)
            T.app.settings.setOnboarded()
        }
        runBlocking { T.app.repository.createFromTemplate(Templates.first { it.key == "office" }, activate = true) }
        DaySelection.select(LocalDate.now().with(DayOfWeek.MONDAY))

        T.launch(compose, "oscuro", language = "en") {
            compose.waitFor(card(s(R.string.tpl_act_work)))
            shot("20_en_oscuro_hoy")

            compose.clickFirst(tab(s(R.string.tab_week)))
            compose.waitFor(hasText(s(R.string.this_week)))
            shot("21_en_oscuro_semana")

            compose.clickFirst(tab(s(R.string.tab_schedules)))
            compose.waitFor(hasText(s(R.string.tpl_office), substring = true))
            shot("22_en_oscuro_horarios")

            compose.clickFirst(tab(s(R.string.tab_progress)))
            compose.waitFor(hasText(s(R.string.stats_this_week).uppercase()))
            shot("23_en_oscuro_progreso")

            compose.clickFirst(tab(s(R.string.tab_settings)))
            compose.waitFor(hasText(s(R.string.settings_about).uppercase()))
            shot("24_en_oscuro_ajustes")
        }
    }
}
