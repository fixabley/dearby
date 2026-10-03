package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
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
@Composable fun DearbyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = Button(onClick, modifier.heightIn(min = 50.dp), enabled = enabled, shape = RoundedCornerShape(11.dp), content = content)
@Composable fun DearbyOutlineButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = OutlinedButton(onClick, modifier.heightIn(min = 50.dp), enabled = enabled, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal), border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) Teal else Line), content = content)

@Composable fun ScreenHeader(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (back != null) IconButton(back) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", tint = Teal) }
        else Spacer(Modifier.width(20.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = if (back == null) MaterialTheme.colorScheme.onSurface else Teal)
        actions()
    }
}

@Composable fun ExampleBadge(text: String = "예시", accent: Boolean = false) {
    Surface(color = if (accent) Mint else Soft, shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = if (accent) Teal else Quiet, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = Quiet)
        Text(label, Modifier.width(72.dp), color = Quiet, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun FormColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)

@Composable fun Field(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) = OutlinedTextField(
    value, change, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, shape = RoundedCornerShape(10.dp))

@Composable fun PersonHeader(name: String, job: String, introduction: String, filled: Boolean = false) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(76.dp).background(if (filled) Teal else Mint, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(name.take(1), color = if (filled) Color.White else Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            if (introduction.isNotBlank()) Text(introduction, style = MaterialTheme.typography.bodyMedium)
            if (job.isNotBlank()) Text(job, color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun ContactSymbol(kind: String, modifier: Modifier = Modifier, tint: Color = Teal) {
    when (kind) {
        "github" -> Icon(painterResource(R.drawable.github_mark), null, modifier, tint = tint)
        "behance" -> Text("Bē", modifier, color = tint, fontWeight = FontWeight.Bold)
        else -> Icon(when (kind) {
            "phone" -> androidx.compose.material.icons.Icons.Outlined.Phone
            "email" -> androidx.compose.material.icons.Icons.Outlined.Email
            "kakao" -> androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline
            "instagram" -> androidx.compose.material.icons.Icons.Outlined.CameraAlt
            else -> androidx.compose.material.icons.Icons.Outlined.Link
        }, null, modifier, tint = tint)
    }
}

@Composable fun TimelineEntry(date: String, title: String, subtitle: String = "", last: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(10.dp).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            if (!last) Box(Modifier.padding(top = 8.dp).width(1.dp).fillMaxHeight().background(Line))
            Box(Modifier.padding(top = 5.dp).size(8.dp).background(Teal, androidx.compose.foundation.shape.CircleShape))
        }
        if (compact) Row(Modifier.weight(1f).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(date, Modifier.weight(.4f), color = Quiet, style = MaterialTheme.typography.bodySmall)
            Text(title, Modifier.weight(.6f), style = MaterialTheme.typography.bodySmall)
        } else Column(Modifier.weight(1f).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (date.isNotBlank()) Text(date, color = Quiet, style = MaterialTheme.typography.bodySmall)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, color = Quiet, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
