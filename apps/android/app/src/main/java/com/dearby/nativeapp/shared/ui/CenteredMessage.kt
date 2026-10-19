package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A CTA on a [CenteredMessage]: [busy] swaps the label for a spinner and disables the button; [testTag] keeps existing UI test tags. */
data class MessageAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true, val busy: Boolean = false, val testTag: String? = null)

/**
 * 비스크롤 화면의 한 가지 배치(contract "비스크롤 화면 배치·CTA"·"아이콘 통일", 2026-10-19 밤): 빈 상태·오류·시트·동의·QR 같은 화면은 카드 없이 배경 위에
 * 아이콘(청록 채움 원 64 안의 흰 Rounded 글리프 28, [error]면 `error` 채움)·제목(`headlineSmall` 굵게)·설명(`bodyLarge` 보조색, 최대 3줄)을 가운데 두고, 행동은 아래쪽
 * 전체 너비 주 버튼 [primary]과 그 밑 텍스트 버튼 [secondary]로 둔다. 좌우 여백 24, 아래 16. 다른 글자 크기는 섞지 않는다.
 *
 * [symbol]은 아이콘 원 대신 그릴 것(로그인·시작 화면의 `DearbyBrand`, QR 이미지), [content]는 설명 아래 가운데 내용(동의 체크 행·오류 문구·활동 칩), [actions]는 주 버튼과
 * 텍스트 버튼 사이에 세로로 쌓이는 추가 버튼(Apple·Google 로그인; 가로 분할 금지).
 *
 * 세로 가운데 정렬과 넘침 스크롤: 호출 측이 높이를 채우면(`Modifier.fillMaxSize()`처럼 최소 높이 = 최대 높이) 열의 최소 높이를 그 높이로 두고 위아래 weight 여백으로
 * 가운데 맞춘다(`verticalScroll` 뒤에 `heightIn(min)`을 붙여야 최대 높이가 무한해도 weight가 최소 높이를 기준으로 나뉜다). 내용이 더 길면 그대로 스크롤된다.
 * 시트처럼 높이가 내용에 맞는 곳에서는 최소 높이 없이 내용 높이만큼 그린다. `DearbyApp`이 인셋을 한 번 적용하므로 여기서는 안전 영역 여백을 더하지 않는다.
 */
@Composable fun CenteredMessage(
    icon: ImageVector?, title: String, message: String?, modifier: Modifier = Modifier, error: Boolean = false,
    primary: MessageAction? = null, secondary: MessageAction? = null,
    symbol: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}, actions: @Composable ColumnScope.() -> Unit = {},
) = BoxWithConstraints(modifier) {
    val fills = constraints.hasBoundedHeight && constraints.minHeight == constraints.maxHeight
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = if (fills) maxHeight else 0.dp).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        when {
            symbol != null -> symbol()
            icon != null -> StateIcon(icon, error)
        }
        if (symbol != null || icon != null) Spacer(Modifier.height(20.dp))
        Text(title, Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
        if (!message.isNullOrBlank()) Text(message, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            primary?.let { action ->
                DearbyButton(action.onClick, Modifier.fillMaxWidth().tagged(action.testTag), enabled = action.enabled && !action.busy) {
                    if (action.busy) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp) else Text(action.label)
                }
            }
            actions()
            secondary?.let { action ->
                TextButton(action.onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).tagged(action.testTag), enabled = action.enabled && !action.busy) { Text(action.label) }
            }
        }
    }
}

private fun Modifier.tagged(tag: String?) = if (tag == null) this else testTag(tag)

/** 상태 아이콘(contract "아이콘 통일" ③): 청록 채움 원 64 안의 흰 글리프 28; [error]는 `error` 채움. */
@Composable private fun StateIcon(icon: ImageVector, error: Boolean) =
    Box(Modifier.size(64.dp).background(if (error) MaterialTheme.colorScheme.error else TealFill, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(28.dp), tint = Color.White)
    }
