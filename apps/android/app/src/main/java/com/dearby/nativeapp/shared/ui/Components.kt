package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dearby.nativeapp.R

val Teal = Color(0xFF007F80)
val Mint = Color(0xFFF0FCFA)
val Quiet = Color(0xFF657078)
val Line = Color(0xFFE3E8EA)
val Soft = Color(0xFFF5F7F8)

@Composable fun DearbyTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(primary = Teal, secondary = Teal, background = Color.White, surface = Color.White, onSurface = Color(0xFF172027), onSurfaceVariant = Quiet, surfaceVariant = Soft, outlineVariant = Line, surfaceContainer = Color.White, secondaryContainer = Mint, onSecondaryContainer = Teal, primaryContainer = Mint, onPrimaryContainer = Teal),
    typography = Typography(headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold), headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold), titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold), titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)),
    shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(12.dp)), content = content)
@Composable fun DearbyLogo(modifier: Modifier = Modifier) = Image(painterResource(R.drawable.dearby_logo), "dearby", modifier.width(92.dp).height(34.dp))
@Composable fun DearbyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(10.dp), content = content)
@Composable fun DearbyOutlineButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal), border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) Teal else Line), content = content)
