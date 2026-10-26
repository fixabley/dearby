package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
/** 로그인 시트 락업: 가로 워드마크 아래 캐치프레이즈를 한 장으로 그린 이미지(`dearby_lockup`, 원본 shared/assets/brand/sign-in-lockup.html; 다크는 drawable-night-nodpi).
 *  고정 높이 96dp(비율 1240:480, 글자 배율과 무관): 절반 높이 로그인 시트의 약 1/4. */
@Composable fun DearbyLockup(modifier: Modifier = Modifier) =
    Image(painterResource(R.drawable.dearby_lockup), "Dearby, 대외활동, 매일 맞춤으로", modifier.height(96.dp).aspectRatio(1240f / 480f))
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
 * Sign in with Google (contract "구글 로그인"): Google's light/dark button colors (fill #FFFFFF / #131314, 1dp stroke #747775 / #8E918F, text #1F1F1F / #E3E3E3,
 * branding guidelines 2026-07-07), same size as the other sign-in buttons. The 20dp "G" is the official asset (shared/assets/prototype/google-sign-in); its
 * corners carry the button fill, so the fill must stay exactly these values.
 */
@Composable fun DearbyGoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = isSystemInDarkTheme()
    val fill = Color(if (dark) 0xFF131314 else 0xFFFFFFFF)
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, border = BorderStroke(1.dp, Color(if (dark) 0xFF8E918F else 0xFF747775)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fill, disabledContainerColor = fill, contentColor = Color(if (dark) 0xFFE3E3E3 else 0xFF1F1F1F))) {
        Image(painterResource(R.drawable.ic_google_g), null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Google로 계속하기")
    }
}

/**
 * 화면 상단 바(contract "iOS 상단 바는 시스템 내비게이션 바" Android 문장, 2026-10-20): Material 3 `TopAppBar` 그대로 — 제목 `titleLarge` 한 줄, [back]이 있으면
 * `navigationIcon` 뒤로, 오른쪽 [actions](탭 루트는 [TabActions]). 바탕은 페이지 `background`와 같고, [scrollBehavior](탭 루트의 `pinnedScrollBehavior`)에 내용 스크롤이
 * 이어지면 시스템이 `surfaceContainer`로 올려 구분한다. 상태 표시줄 인셋은 바가 직접 받는다(`DearbyApp`은 상태 표시줄 여백을 두지 않는다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DearbyTopBar(title: String, back: (() -> Unit)? = null, scrollBehavior: TopAppBarScrollBehavior? = null, actions: @Composable RowScope.() -> Unit = {}) =
    TopAppBar({ Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { back?.let { IconButton(it) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") } } },
        actions = actions, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), scrollBehavior = scrollBehavior)

/**
 * 검색창 하나(contract "퍼널 검색은 발견 검색과 같은 경험"; lead 2026-10-20 "검색창과 입력 칸은 같은 모양"): `DearbyField`와 같은 `FieldBox`(둥근 10, `surfaceContainerHighest`, 높이 48, 테두리 없음)
 * 안의 Compose `BasicTextField` — 왼쪽 돋보기, 값이 있으면 오른쪽 지우기 `Cancel`, `ImeAction.Search`는 키보드만 내린다(결과는 입력과 함께 이미 바뀐다). 접근성 이름은 [placeholder].
 * Material `SearchBarDefaults.InputField`는 최소 높이 56과 알약 모양이 고정이라 쓰지 않는다. 발견·받은 명함·이력 퍼널·직무 선택이 같은 것을 쓴다.
 */
@Composable fun DearbySearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, focusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge
    FieldBox(modifier, leadingIcon = Icons.Rounded.Search, trailing = if (query.isEmpty()) null else { { IconButton({ onQueryChange("") }) { Icon(Icons.Rounded.Cancel, "검색어 지우기", tint = colors.onSurfaceVariant) } } }) {
        androidx.compose.foundation.text.BasicTextField(query, onQueryChange,
            Modifier.weight(1f).padding(vertical = FIELD_PADDING_VERTICAL).then(if (focusRequester == null) Modifier else Modifier.focusRequester(focusRequester)).semantics { contentDescription = placeholder },
            textStyle = style.copy(color = colors.onSurface), singleLine = true, cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() })) { inner ->
            Box { if (query.isEmpty()) Text(placeholder, color = colors.onSurfaceVariant, style = style); inner() }
        }
    }
}

/** 상단 바 드롭다운 메뉴의 한 항목(contract "상단 바 메뉴", 2026-10-20): 아이콘 + 라벨. */
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * 탭 첫 화면 상단 바의 공통 오른쪽 끝(contract "상단 바 구성(네 탭 공통)", 2026-10-20): [설정] [알림] [메뉴] 순서의 48dp `IconButton`(44 터치 영역 이상). 탭 고유 주 행동(프로필의
 * `수정`)은 호출 측이 이 앞에 둔다. [menu]가 비어 있지 않으면 `Menu` 아이콘(접근성 `더 보기`)이 `DropdownMenu`를 열고 항목은 `DropdownMenuItem(leadingIcon + text)`다; 비면 메뉴 버튼이 없다.
 * [notify]는 알림 목록을 연다(2026-10-25 "알림 목록(발견 우상단 종)").
 */
@Composable fun TabActions(settings: () -> Unit, notify: () -> Unit, menu: List<MenuAction> = emptyList(), unread: Int = 0) {
    IconButton(settings) { Icon(Icons.Rounded.Settings, "설정") }
    // [unread] (알림 목록, 2026-10-25): a number badge, `9+` above nine.
    IconButton(notify) {
        BadgedBox({ if (unread > 0) Badge { Text(if (unread > 9) "9+" else unread.toString()) } }) {
            Icon(Icons.Rounded.Notifications, if (unread > 0) "알림, 읽지 않음 ${unread}개" else "알림")
        }
    }
    if (menu.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Rounded.Menu, "더 보기") }
        DropdownMenu(open, { open = false }) {
            menu.forEach { item -> DropdownMenuItem({ Text(item.label) }, { open = false; item.onClick() }, leadingIcon = { Icon(item.icon, null) }) }
        }
    }
}

/** [danger] reads in the danger colour on the quiet background (a 탈락 mark). */
/** [icon]: a decorative 16dp icon before the text (the detail's 종류 chip). */
@Composable fun ExampleBadge(text: String = "예시", accent: Boolean = false, danger: Boolean = false, icon: ImageVector? = null) {
    val color = if (danger) MaterialTheme.colorScheme.error else if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = if (accent && !danger) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = color)
            Text(text, color = color, style = MaterialTheme.typography.labelMedium)
        }
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
@Composable fun PersonHeader(name: String, job: String, filled: Boolean = false) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(76.dp).background(if (filled) TealFill else MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(name.take(1), color = if (filled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.headlineMedium)
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

/**
 * 연락처 종류 → 왼쪽 글리프(contract "연락처 행은 라벨 대신 왼쪽 아이콘" 2026-10-20, "연락처 아이콘·바로가기" 2026-10-26): GitHub·Instagram·Behance·카카오톡은
 * Simple Icons 단색 마크(`res/drawable/ic_brand_*`, Material 기호와 같은 여백), 그 밖은 Material 기호. 표시 행·입력 행·`연락처 추가` 목록이 모두 이것을 보조색 틴트로 쓴다.
 */
@Composable fun contactIcon(kind: String): ImageVector = when (kind) {
    "phone" -> Icons.Rounded.Call
    "email" -> Icons.Rounded.Mail
    "kakao" -> ImageVector.vectorResource(R.drawable.ic_brand_kakaotalk)
    "instagram" -> ImageVector.vectorResource(R.drawable.ic_brand_instagram)
    "github" -> ImageVector.vectorResource(R.drawable.ic_brand_github)
    "behance" -> ImageVector.vectorResource(R.drawable.ic_brand_behance)
    else -> Icons.Rounded.Link
}

/** 표시 행(명함 면·받은 명함·내 프로필 카드)의 왼쪽 아이콘: [contactIcon]을 24, 보조색으로. */
@Composable fun ContactKindIcon(kind: String, contentDescription: String, modifier: Modifier = Modifier) =
    Icon(contactIcon(kind), contentDescription, modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)

/** `시간표` with the timeline icon, the same mark the activity card shows (contract "트랙·세션 시간표"). */
@Composable fun TimetableMark(modifier: Modifier = Modifier) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Rounded.ViewTimeline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text("시간표", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
