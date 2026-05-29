package org.gradle.profiler.studio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.theme.JewelTheme

data class StudioPalette(
    val windowBg: Color,
    val sidebarBg: Color,
    val selectedRowBg: Color,
    val tabStripBg: Color,
    val selectedTabBg: Color,
    val subtabBarBg: Color,
    val resultsTile: Color,
    val divider: Color,
    val mutedText: Color,
    val accent: Color,
    val textPrimary: Color,
)

private val LightPalette = StudioPalette(
    windowBg = Color(0xFFFFFFFF),
    sidebarBg = Color(0xFFF2F2F2),
    selectedRowBg = Color(0xFFD7E4F2),
    tabStripBg = Color(0xFFEDEDED),
    selectedTabBg = Color(0xFFFFFFFF),
    subtabBarBg = Color(0xFFFAFAFA),
    resultsTile = Color(0xFFF2F2F2),
    divider = Color(0xFFCCCCCC),
    mutedText = Color(0xFF666666),
    accent = Color(0xFF2196F3),
    textPrimary = Color(0xFF000000),
)

private val DarkPalette = StudioPalette(
    windowBg = Color(0xFF2B2B2B),
    sidebarBg = Color(0xFF313335),
    selectedRowBg = Color(0xFF3B5974),
    tabStripBg = Color(0xFF3C3F41),
    selectedTabBg = Color(0xFF2B2B2B),
    subtabBarBg = Color(0xFF313335),
    resultsTile = Color(0xFF3C3F41),
    divider = Color(0xFF4C5052),
    mutedText = Color(0xFF9E9E9E),
    accent = Color(0xFF4FC3F7),
    textPrimary = Color(0xFFE0E0E0),
)

val StudioColors: StudioPalette
    @Composable
    get() = if (JewelTheme.isDark) DarkPalette else LightPalette
