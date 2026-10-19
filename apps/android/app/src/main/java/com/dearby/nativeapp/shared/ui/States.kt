package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// 목록 화면의 불러오는 중·오류·빈 결과. 오류·빈 결과는 `CenteredMessage` 배치(contract "비스크롤 화면 배치·CTA") 그대로이고 화면 읽기 도구 알림만 더한다. 문구는 호출 측이 넘긴다.

/** 불러오는 중: 가운데 표시기와 문구. 화면 읽기 도구에는 [message]를 조용히 알린다. */
@Composable fun DearbyLoadingState(message: String, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp).semantics { liveRegion = LiveRegionMode.Polite },
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
    CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeWidth = 3.dp)
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
}

/** 오류와 다시 시도(`error` 채움 원의 `Warning`). 화면 읽기 도구에 바로 알린다. */
@Composable fun DearbyErrorState(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, retryLabel: String = "다시 시도") =
    CenteredMessage(Icons.Rounded.Warning, title, message, modifier.semantics { liveRegion = LiveRegionMode.Assertive }, error = true, primary = MessageAction(retryLabel, onRetry))

/** 빈 결과. [action]은 다음 행동 주 버튼 하나. */
@Composable fun DearbyEmptyState(title: String, message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Explore, action: MessageAction? = null) =
    CenteredMessage(icon, title, message, modifier.semantics { liveRegion = LiveRegionMode.Polite }, primary = action)
