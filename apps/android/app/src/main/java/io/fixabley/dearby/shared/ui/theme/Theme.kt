package io.fixabley.dearby.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Complete native role sets: do not mix a brand primary with unrelated default roles.
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()
private val NativeTypography = Typography()
private val NativeShapes = Shapes()

/** Android 12+ wallpaper colors by default; opt out for deterministic previews/tests. */
@Composable
fun DearbyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = NativeTypography,
        shapes = NativeShapes,
        content = content,
    )
}
