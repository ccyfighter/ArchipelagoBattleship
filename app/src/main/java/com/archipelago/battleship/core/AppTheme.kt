package com.archipelago.battleship.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode { PAPER, SONAR }

data class BoardPalette(
    val bg: Color,
    val grid: Color,
    val label: Color,
    val water: Color,
    val mountain: Color,
    val shallow: Color,
    val ship: Color,
    val sunk: Color,
    val hit: Color,
    val miss: Color,
    val accent: Color,
    val legal: Color,
    val select: Color
)

fun palette(mode: AppThemeMode): BoardPalette = if (mode == AppThemeMode.PAPER) {
    BoardPalette(
        bg = Color(0xFFF4EBD9),
        grid = Color(0xFF2F5C8A),
        label = Color(0xFF2F5C8A),
        water = Color(0xFFEFE4CC),
        mountain = Color(0xFF8C7B62),
        shallow = Color(0xFFDCE6DA),
        ship = Color(0xFF234E77),
        sunk = Color(0xFF9A9A9A),
        hit = Color(0xFFC0392B),
        miss = Color(0xFF2F6FA8),
        accent = Color(0xFF1F4E79),
        legal = Color(0x553A7BC0),
        select = Color(0xFFD35400)
    )
} else {
    BoardPalette(
        bg = Color(0xFF0B192C),
        grid = Color(0xFF35E0C0),
        label = Color(0xFF7FE9D4),
        water = Color(0xFF102438),
        mountain = Color(0xFF274B4A),
        shallow = Color(0xFF17394A),
        ship = Color(0xFF35E0C0),
        sunk = Color(0xFF5C7A8C),
        hit = Color(0xFFFF6B4A),
        miss = Color(0xFF4FC3F7),
        accent = Color(0xFF35E0C0),
        legal = Color(0x4448E0B0),
        select = Color(0xFFFFC107)
    )
}

@Composable
fun ArchipelagoTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val scheme = if (mode == AppThemeMode.SONAR) {
        darkColorScheme(
            primary = Color(0xFF35E0C0),
            onPrimary = Color(0xFF052018),
            secondary = Color(0xFFFF8A65),
            tertiary = Color(0xFFFFC107),
            background = Color(0xFF0B192C),
            onBackground = Color(0xFFD5F2EA),
            surface = Color(0xFF12263F),
            onSurface = Color(0xFFD5F2EA),
            surfaceVariant = Color(0xFF17334F),
            onSurfaceVariant = Color(0xFF9FD8CC),
            outline = Color(0xFF3A6E78),
            error = Color(0xFFFF6B4A)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1F4E79),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFFB23B3B),
            tertiary = Color(0xFFC77B22),
            background = Color(0xFFF4EBD9),
            onBackground = Color(0xFF1B3350),
            surface = Color(0xFFFFFCF3),
            onSurface = Color(0xFF1B3350),
            surfaceVariant = Color(0xFFEADFC5),
            onSurfaceVariant = Color(0xFF4A5E70),
            outline = Color(0xFF9AA9B4),
            error = Color(0xFFC0392B)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
