package com.eduardo.horarios

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.eduardo.horarios.data.localized

/** Utilidades compartidas por las pruebas en emulador. */
object T {
    val app: HorariosApp get() = ApplicationProvider.getApplicationContext()

    private val ctx: Context get() = app.localized()

    fun s(@StringRes id: Int, vararg args: Any): String = ctx.getString(id, *args)

    fun p(@PluralsRes id: Int, n: Int): String = ctx.resources.getQuantityString(id, n, n)

    fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** Botón del menú inferior con ese texto. */
    fun tab(label: String): SemanticsMatcher =
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** Tarjeta pulsable que contiene ese texto. */
    fun card(text: String): SemanticsMatcher = hasText(text, substring = true) and hasClickAction()

    /** Captura de toda la pantalla (barra de estado y menú incluidos). */
    fun shot(compose: ComposeTestRule, name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        TestStorage().openOutputFile("screenshots/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun dumpTree(compose: ComposeTestRule, name: String) {
        runCatching {
            val tree = compose.onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)
            TestStorage().openOutputFile("screenshots/$name.txt").use { it.write(tree.toByteArray()) }
        }
    }

    /**
     * Abre la app en [language] y, si algo falla, guarda captura y árbol de pantalla antes de cerrarla.
     * El idioma se aplica con la app ya abierta: AppCompat solo puede cambiarlo si hay una pantalla activa.
     */
    fun launch(compose: ComposeTestRule, name: String, language: String, block: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                onMain { app.settings.setLanguage(language) }
                val deadline = System.currentTimeMillis() + 15_000
                var current = ""
                while (System.currentTimeMillis() < deadline) {
                    scenario.onActivity { current = it.resources.configuration.locales[0].language }
                    if (current == language) break
                    Thread.sleep(200)
                }
                check(current == language) { "La app no cambió al idioma $language (sigue en $current)" }
                compose.waitForIdle()
                block()
            } catch (t: Throwable) {
                runCatching { shot(compose, "FALLO_$name") }
                dumpTree(compose, "FALLO_$name")
                throw t
            }
        }
    }
}

fun ComposeTestRule.waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeTestRule.waitGone(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) {
    waitUntil(timeoutMs) { onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
}

fun ComposeTestRule.clickFirst(matcher: SemanticsMatcher) {
    waitFor(matcher)
    onAllNodes(matcher).onFirst().performClick()
}
