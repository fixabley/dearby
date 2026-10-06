package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// 목록 화면의 불러오는 중·오류·빈 결과. iOS ContentUnavailableView와 같은 배치(가운데 아이콘·제목·설명·행동)이고 웹 states와 모양을 맞춘다. 문구는 호출 측이 넘긴다.

/** 불러오는 중. 화면 읽기 도구에는 [message]를 조용히 알린다. */
@Composable fun DearbyLoadingState(message: String, modifier: Modifier = Modifier) = StateLayout(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
    CircularProgressIndicator(Modifier.size(28.dp), color = Teal, trackColor = Line, strokeWidth = 3.dp)
    Text(message, color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
}

/** 오류와 다시 시도. 화면 읽기 도구에 바로 알린다. */
@Composable fun DearbyErrorState(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, retryLabel: String = "다시 시도") =
    StateLayout(modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
        StateIcon(Icons.Outlined.ErrorOutline)
        StateText(title, message)
        DearbyOutlineButton(onRetry, Modifier.padding(top = 8.dp)) { Text(retryLabel, style = MaterialTheme.typography.titleMedium) }
    }

/** 빈 결과. [action]으로 다음 행동 버튼을 하나 둘 수 있다. */
@Composable fun DearbyEmptyState(title: String, message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.Explore, action: (@Composable () -> Unit)? = null) =
    StateLayout(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        StateIcon(icon)
        StateText(title, message)
        if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
    }

@Composable private fun StateLayout(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), content = content)

@Composable private fun StateIcon(icon: ImageVector) = Box(Modifier.size(68.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, null, Modifier.size(32.dp), tint = Teal)
}

@Composable private fun StateText(title: String, message: String) = Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
    if (message.isNotBlank()) Text(message, color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
}
