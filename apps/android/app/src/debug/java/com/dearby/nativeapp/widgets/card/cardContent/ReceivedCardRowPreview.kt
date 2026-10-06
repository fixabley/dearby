package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbySectionHeader
import com.dearby.nativeapp.shared.ui.DearbyTheme

/** 미리보기와 계측 캡처가 함께 쓰는 받은 명함 묶음 예시. */
@Composable fun ReceivedCardGroupsSample() {
    var open by remember { mutableStateOf(true) }
    Column(Modifier.background(Color.White).padding(20.dp)) {
        DearbySectionHeader("Dearby 개발자 컨퍼런스", 2, expanded = open, onToggle = { open = !open })
        if (open) {
            ReceivedCardRow("이서연", "프로덕트 디자이너", {}, activityTitle = "Dearby 개발자 컨퍼런스", otherActivityCount = 1)
            HorizontalDivider()
            ReceivedCardRow("박준호", "백엔드 개발", {}, activityTitle = "Dearby 개발자 컨퍼런스")
        }
        DearbySectionHeader("활동 없음", 1, expanded = true)
        ReceivedCardRow("최유나", "커뮤니티 매니저", {})
    }
}

@Preview(name = "받은 명함 묶음", widthDp = 390) @Composable private fun ReceivedCardGroupsPreview() = DearbyTheme { ReceivedCardGroupsSample() }
