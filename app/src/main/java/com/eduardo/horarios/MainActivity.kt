package com.eduardo.horarios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eduardo.horarios.ui.AppNavigation
import com.eduardo.horarios.ui.theme.HorariosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HorariosTheme {
                AppNavigation()
            }
        }
    }
}
