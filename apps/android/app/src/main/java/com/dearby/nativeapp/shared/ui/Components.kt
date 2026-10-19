package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dearby.nativeapp.R

/** Pretendard 가변 글꼴 하나에서 앱이 쓰는 굵기(400·500·600·700)를 뽑는다. 원본: 저장소 루트 shared/assets/fonts/pretendard. */
@OptIn(ExperimentalTextApi::class)
val Pretendard = FontFamily(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
    Font(R.font.pretendard_variable, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
})

// 크기·굵기는 기존 값 그대로 두고 모든 글자 스타일의 글꼴만 Pretendard로 바꾼다.
private val DearbyTypography = Typography(headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold), headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold), titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold), titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)).run {
    Typography(displayLarge.copy(fontFamily = Pretendard), displayMedium.copy(fontFamily = Pretendard), displaySmall.copy(fontFamily = Pretendard),
        headlineLarge.copy(fontFamily = Pretendard), headlineMedium.copy(fontFamily = Pretendard), headlineSmall.copy(fontFamily = Pretendard),
        titleLarge.copy(fontFamily = Pretendard), titleMedium.copy(fontFamily = Pretendard), titleSmall.copy(fontFamily = Pretendard),
        bodyLarge.copy(fontFamily = Pretendard), bodyMedium.copy(fontFamily = Pretendard), bodySmall.copy(fontFamily = Pretendard),
        labelLarge.copy(fontFamily = Pretendard), labelMedium.copy(fontFamily = Pretendard), labelSmall.copy(fontFamily = Pretendard))
}

/**
 * 색 체계(contract "색 체계"·"다크 모드", 2026-10-19 밤): 청록 시드 #007F80의 Material 3 톤(materialyoucolor 3.0.4 TonalSpot, 2025 spec)으로 라이트·다크
 * 스킴을 두고 다이내믹 컬러는 켜지 않는다. 덮어쓴 값(iOS와의 균형, 2026-10-19 밤 후속): primary는 브랜드(라이트 #007F80, 다크 #3FB3B4); primaryContainer는 옅은
 * 청록(라이트 #D9F2F1/#004F50, 다크 #1D4140/#BDEDED); 묶음 배경식 분리로 `background`는 surfaceContainerLow 톤(라이트 #EEF5F4, 다크 #0E1515), 카드·시트·타일의
 * `surface`는 라이트 흰색·다크 #131B1B(배경보다 밝다). 그 밖의 중립색은 시드 톤이라 손으로 정한 상수는 없다; 화면은 `primary`·`primaryContainer`/`onPrimaryContainer`·
 * `surface`·`surfaceContainer`·`surfaceContainerHighest`(카드 선·구분선)·`onSurfaceVariant`·`onSurface`·`error`만 읽는다. 시스템 설정을 따르며 라이트를 강제하지 않는다.
 * 청록 채움 면(contract "청록 채움 면 위 글자는 흰색", 2026-10-19 밤): 채운 버튼·배지·칩·아바타·상태 원은 두 모드 모두 [TealFill](#007F80) 바탕에 `Color.White` 글자·아이콘을
 * 명시한다. `primary`는 글자·아이콘·테두리 액센트(라이트 #007F80, 다크 #3FB3B4)로만 읽고, 다크 `onPrimary`도 흰색이라 이전의 어두운 #0C4848은 어디에도 없다.
 */
/** 채움 면의 청록: 라이트·다크 같은 값(흰 글자 대비 4.8:1). 글자·아이콘 액센트는 `colorScheme.primary`. */
val TealFill = Color(0xFF007F80)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF007F80), onPrimary = Color.White, primaryContainer = Color(0xFFD9F2F1), onPrimaryContainer = Color(0xFF004F50), inversePrimary = Color(0xFFB6FEFE),
    secondary = Color(0xFF4A6464), onSecondary = Color(0xFFE2FEFE), secondaryContainer = Color(0xFFCCE8E7), onSecondaryContainer = Color(0xFF3D5656),
    tertiary = Color(0xFF3A647D), onTertiary = Color(0xFFF5F9FF), tertiaryContainer = Color(0xFFAAD4F2), onTertiaryContainer = Color(0xFF1E4A62),
    error = Color(0xFFA83836), onError = Color(0xFFFFF7F6), errorContainer = Color(0xFFFA746F), onErrorContainer = Color(0xFF6E0A12),
    background = Color(0xFFEEF5F4), onBackground = Color(0xFF2A3434), surface = Color.White, onSurface = Color(0xFF2A3434),
    surfaceVariant = Color(0xFFD9E5E4), onSurfaceVariant = Color(0xFF566161), outline = Color(0xFF727D7C), outlineVariant = Color(0xFFA9B4B4),
    inverseSurface = Color(0xFF0A0F0F), inverseOnSurface = Color(0xFF999E9D), scrim = Color.Black,
    surfaceDim = Color(0xFFD1DCDC), surfaceBright = Color(0xFFF6FAF9), surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF5F4),
    surfaceContainer = Color(0xFFE7EFEF), surfaceContainerHigh = Color(0xFFE1EAE9), surfaceContainerHighest = Color(0xFFD9E5E4),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3FB3B4), onPrimary = Color.White, primaryContainer = Color(0xFF1D4140), onPrimaryContainer = Color(0xFFBDEDED), inversePrimary = Color(0xFF336767),
    secondary = Color(0xFFB0CCCB), onSecondary = Color(0xFF2B4445), secondaryContainer = Color(0xFF274040), onSecondaryContainer = Color(0xFFA9C5C4),
    tertiary = Color(0xFFBAE2FF), onTertiary = Color(0xFF28536B), tertiaryContainer = Color(0xFFAAD4F2), onTertiaryContainer = Color(0xFF1E4A62),
    error = Color(0xFFFA746F), onError = Color(0xFF490006), errorContainer = Color(0xFF871F21), onErrorContainer = Color(0xFFFF9993),
    background = Color(0xFF0E1515), onBackground = Color(0xFFDCE8E7), surface = Color(0xFF131B1B), onSurface = Color(0xFFDCE8E7),
    surfaceVariant = Color(0xFF1D2828), onSurfaceVariant = Color(0xFFA2ADAD), outline = Color(0xFF6D7877), outlineVariant = Color(0xFF3F4A4A),
    inverseSurface = Color(0xFFF6FAF9), inverseOnSurface = Color(0xFF515656), scrim = Color.Black,
    surfaceDim = Color(0xFF0A0F0F), surfaceBright = Color(0xFF242E2E), surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0E1515),
    surfaceContainer = Color(0xFF131B1B), surfaceContainerHigh = Color(0xFF192121), surfaceContainerHighest = Color(0xFF1D2828),
)

@Composable fun DearbyTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
    typography = DearbyTypography,
    shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(12.dp)), content = content)
/** 심볼(맞물린 두 고리, 청록 단색이라 `primary`로 물들여 다크에서는 밝은 청록) 아래 앱 이름. 인트로·로그인 화면에만 쓴다(2026-10-19 사용자 결정: 일반 화면에는 로고를 두지 않는다). */
@Composable fun DearbyBrand(modifier: Modifier = Modifier, symbolSize: Dp = 72.dp) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Image(painterResource(R.drawable.dearby_symbol), "Dearby", Modifier.size(symbolSize), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary))
    Spacer(Modifier.height(12.dp))
    Text("Dearby", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
}
/**
 * 주 버튼(contract "폼은 플랫폼 기본 컴포넌트", 2026-10-19 밤): Material 3 `Button`/`OutlinedButton` 기본 모양·글꼴에 최소 높이 48dp(터치 영역 규칙)만 둔다.
 * 사용자 정의 모서리·높이·글자 크기는 없다. 채운 버튼은 두 모드 모두 [TealFill] 바탕·흰 글자(contract "청록 채움 면 위 글자는 흰색"); 비활성 색은 Material 기본.
 */
@Composable fun DearbyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = TealFill, contentColor = Color.White), content = content)
@Composable fun DearbyOutlineButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, content = content)
/** Sign in with Apple: Apple's black button (white on a dark theme, per the HIG), same size as the other sign-in buttons. No Apple logo asset is in the repo, so the label is text only. */
@Composable fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) = Button(onClick, modifier.heightIn(min = 48.dp),
    enabled = enabled, colors = if (isSystemInDarkTheme()) ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black) else ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) { Text("Apple로 계속하기") }
/**
 * Sign in with Google (contract "구글 로그인"): theme surface with an outline border, same size as the other sign-in buttons. The official "G" logo asset is
 * not in the repo yet (to be placed under shared/assets/prototype), so a bold "G" stands in for it.
 */
@Composable fun DearbyGoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) = OutlinedButton(onClick, modifier.heightIn(min = 48.dp),
    enabled = enabled, colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
    Text("G", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.width(8.dp))
    Text("Google로 계속하기")
}

/**
 * 화면 제목 줄. [back]이 없으면 다섯 탭의 첫 화면 제목(contract "탭 제목 통일·머리 간소화", 2026-10-19 밤): 왼쪽 정렬 굵은 `titleLarge`, 좌우 20, 상태 표시줄 바로
 * 아래 위 여백 8(상태 표시줄 인셋은 `DearbyApp`이 한 번만 적용한다), 오른쪽 [actions]. [back]이 있으면 하위 화면의 가운데 정렬 inline 제목 그대로다.
 */
@Composable fun ScreenHeader(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    if (back == null) Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
        actions()
    } else Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(title, Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * 탭 첫 화면 제목 줄의 공통 오른쪽 끝(contract "알림 아이콘은 다섯 탭 모두 우상단 고정"·"설정"): [설정] [알림] 순서로 48dp `IconButton` 둘(44 터치 영역 이상).
 * 탭별 보조 행동은 호출 측이 이 앞에 둔다. 알림 화면은 아직 자리만이라 [notify]는 안내 문구를 연다.
 */
@Composable fun TabActions(settings: () -> Unit, notify: () -> Unit) {
    IconButton(settings) { Icon(Icons.Rounded.Settings, "설정") }
    IconButton(notify) { Icon(Icons.Rounded.Notifications, "알림") }
}

/** [danger] reads in the danger colour on the quiet background (a 탈락 mark). */
@Composable fun ExampleBadge(text: String = "예시", accent: Boolean = false, danger: Boolean = false) {
    Surface(color = if (accent && !danger) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = if (danger) MaterialTheme.colorScheme.error else if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** [filled]: 청록 채움 원(`TealFill`)에 흰 이니셜. */
@Composable fun PersonHeader(name: String, job: String, introduction: String, filled: Boolean = false) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(76.dp).background(if (filled) TealFill else MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(name.take(1), color = if (filled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            if (introduction.isNotBlank()) Text(introduction, style = MaterialTheme.typography.bodyMedium)
            if (job.isNotBlank()) Text(job, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 이니셜 아바타(민트 바탕·청록 글자): 명함 머리는 56dp, 목록 줄은 더 작게 쓴다. [shape]는 기본 원, 발견 프로그램 카드는 둥근 네모다. */
@Composable fun InitialAvatar(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.CircleShape) {
    Box(modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, shape), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
    }
}

/** 연락처 종류 이름(contract "명함 표현"): 라벨이 비었을 때 행 라벨로 쓴다. */
fun contactKindName(kind: String) = when (kind) {
    "phone" -> "전화"; "email" -> "메일"; "kakao" -> "카카오톡"; "instagram" -> "Instagram"; "github" -> "GitHub"; "behance" -> "Behance"
    else -> kind
}

@Composable fun ContactSymbol(kind: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    when (kind) {
        "github" -> Icon(painterResource(R.drawable.github_mark), null, modifier, tint = tint)
        "behance" -> Text("Bē", modifier, color = tint, fontWeight = FontWeight.Bold)
        else -> Icon(when (kind) {
            "phone" -> Icons.Rounded.Call
            "email" -> Icons.Rounded.Mail
            "kakao" -> Icons.Rounded.ChatBubbleOutline
            "instagram" -> Icons.Rounded.CameraAlt
            else -> Icons.Rounded.Link
        }, null, modifier, tint = tint)
    }
}

@Composable fun TimelineEntry(date: String, title: String, subtitle: String, last: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(10.dp).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            if (!last) Box(Modifier.padding(top = 8.dp).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Box(Modifier.padding(top = 5.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
        }
        if (compact) Row(Modifier.weight(1f).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(date, Modifier.weight(.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(title, Modifier.weight(.6f), style = MaterialTheme.typography.bodySmall)
        } else Column(Modifier.weight(1f).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (date.isNotBlank()) Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
