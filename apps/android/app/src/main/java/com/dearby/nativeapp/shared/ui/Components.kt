package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
@Composable fun DearbyOutlineButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(10.dp), content = content)
@Composable fun FormColumn(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
@Composable fun Field(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) = OutlinedTextField(value, change, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, shape = RoundedCornerShape(10.dp))
@Composable fun EmptyPanel(title: String, explanation: String) = FormColumn { Text(title, style = MaterialTheme.typography.headlineMedium); Text(explanation, color = Quiet) }
@Composable fun PersonHeader(name: String, job: String, introduction: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(76.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1), color = Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(name, style = MaterialTheme.typography.headlineMedium); if (introduction.isNotBlank()) Text(introduction); if (job.isNotBlank()) Text(job, color = Quiet, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun ContactSymbol(kind: String, modifier: Modifier = Modifier, tint: Color = Teal) {
    when (kind) {
        "github" -> Text("GH", modifier, color = tint, fontWeight = FontWeight.Bold)
        "behance" -> Text("Bē", modifier, color = tint, fontWeight = FontWeight.Bold)
        else -> Icon(when (kind) { "phone" -> Icons.Outlined.Phone; "email" -> Icons.Outlined.Email; "kakao" -> Icons.Outlined.ChatBubbleOutline; "instagram" -> Icons.Outlined.CameraAlt; else -> Icons.Outlined.Link }, null, modifier, tint = tint)
    }
}
@Composable fun TimelineEntry(date: String, title: String, subtitle: String, last: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(12.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!last) Box(Modifier.padding(top = 8.dp).width(1.dp).fillMaxHeight().background(Line))
            Box(Modifier.padding(top = 5.dp).size(8.dp).background(Teal, CircleShape))
        }
        Column(Modifier.weight(1f).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { if (date.isNotBlank()) Text(date, color = Quiet, style = MaterialTheme.typography.bodySmall); Text(title, style = MaterialTheme.typography.titleMedium); if (subtitle.isNotBlank()) Text(subtitle, color = Quiet, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun QrModeSwitch(show: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(12.dp))) {
        listOf(true to "QR 보여주기", false to "QR 찍기").forEach { (value, title) ->
            TextButton({ change(value) }, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (show == value) Teal else Color.Transparent, contentColor = if (show == value) Color.White else Quiet)) { Text(title, fontWeight = FontWeight.SemiBold) }
        }
    }
}
