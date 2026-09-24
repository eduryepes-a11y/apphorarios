package com.eduardo.horarios

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardo.horarios.data.ThemeMode
import com.eduardo.horarios.ui.AppNavigation
import com.eduardo.horarios.ui.backup.ImportController
import com.eduardo.horarios.ui.theme.HorariosTheme

// AppCompatActivity: necesaria para cambiar el idioma dentro de la app.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleImportIntent(intent)
        val settings = (application as HorariosApp).settings
        val showOnboarding = !settings.onboarded
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val accent by settings.accent.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            HorariosTheme(darkTheme = dark, accentIndex = accent) {
                AppNavigation(showOnboarding = showOnboarding)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    /** Si la app se abre con un archivo de horario (WhatsApp, Archivos…), lo prepara para importar. */
    private fun handleImportIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) ImportController.pending.value = uri
    }
}
