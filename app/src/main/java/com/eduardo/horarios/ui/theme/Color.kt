package com.eduardo.horarios.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Indigo = Color(0xFF5B4FE9)
val IndigoDeep = Color(0xFF1E1B4B)
val Coral = Color(0xFFFF6B6B)
val Mint = Color(0xFF2EC4B6)

/** Paleta para actividades y horarios. */
val Palette = listOf(
    Color(0xFF7C6CF2), // Violeta
    Color(0xFF4C9AFF), // Azul
    Color(0xFF2EC4B6), // Turquesa
    Color(0xFF4CC38A), // Verde
    Color(0xFFFFC857), // Amarillo
    Color(0xFFFF9F5A), // Naranja
    Color(0xFFFF6B6B), // Coral
    Color(0xFFF17AB8), // Rosa
    Color(0xFFB57BFF), // Lavanda
    Color(0xFF7D8BA6), // Pizarra
)

fun paletteColor(index: Int): Color = Palette[index.mod(Palette.size)]

/** Degradado oscurecido a partir de un color, para que el texto blanco se lea bien. */
fun headerBrush(base: Color): Brush = Brush.linearGradient(
    listOf(
        lerp(base, IndigoDeep, 0.15f),
        lerp(base, IndigoDeep, 0.55f),
    )
)
