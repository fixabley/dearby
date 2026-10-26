package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 트랙 색 (contract "트랙·세션 시간표": the server keeps only `position`, the app picks from its own palette in that order). Eight hues
 * as Material tonal pairs — light: tone 90 fill with tone 10 text, dark: tone 30 fill with tone 90 text — so the badge text keeps
 * well over 4.5:1 in both themes. The ninth track reuses the first colour.
 */
data class TrackColor(val container: Color, val content: Color)

@Composable fun trackColor(position: Int): TrackColor {
    val (light, dark) = PALETTE[Math.floorMod(position, PALETTE.size)]
    return if (isSystemInDarkTheme()) TrackColor(Color(dark), Color(light)) else TrackColor(Color(light), Color(INK[Math.floorMod(position, PALETTE.size)]))
}

/** (tone 90, tone 30) per hue: blue, green, orange, purple, teal, pink, amber, indigo. */
private val PALETTE = listOf(
    0xFFD6E3FF to 0xFF00458E, 0xFFC4EED0 to 0xFF005229, 0xFFFFDBC8 to 0xFF763300, 0xFFEBDDFF to 0xFF5A2E9C,
    0xFFB4EFEA to 0xFF004F4B, 0xFFFFD9E2 to 0xFF8E0050, 0xFFFFDF9E to 0xFF5B4300, 0xFFDFE0FF to 0xFF2E3D99,
)
/** Tone 10 of the same hues, the light theme's badge text. */
private val INK = listOf(0xFF001B3E, 0xFF00210E, 0xFF331200, 0xFF250059, 0xFF00201E, 0xFF3E0020, 0xFF261A00, 0xFF000F5D)
