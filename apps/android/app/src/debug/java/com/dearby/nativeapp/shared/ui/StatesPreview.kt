package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

/** 미리보기와 계측 캡처가 함께 쓰는 세 상태 예시. */
@Composable fun DearbyStatesSample(onRetry: () -> Unit = {}) = Column(Modifier.background(Color.White)) {
    DearbyLoadingState("활동을 불러오는 중이에요.")
    HorizontalDivider(color = Line)
    DearbyErrorState("활동을 불러오지 못했어요", "인터넷 연결을 확인하고 다시 시도해 주세요.", onRetry)
    HorizontalDivider(color = Line)
    DearbyEmptyState("모집 중인 활동이 없어요", "새 활동이 공개되면 여기에서 볼 수 있어요.") { DearbyOutlineButton({}) { Text("새로 고침") } }
}

@Preview(name = "상태", widthDp = 390) @Composable private fun StatesPreview() = DearbyTheme { DearbyStatesSample() }
