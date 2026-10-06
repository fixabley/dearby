package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** 미리보기와 계측 캡처가 함께 쓰는 예시. 주소는 실제가 아닌 example.invalid 값이다. */
@Composable fun QrShareSample(url: String? = "https://example.invalid/s/6f1c2a9e-0b7d-4f3e-9a51-2c8e7d4b1a60", errorMessage: String? = null) {
    var mode by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf("conference")) }
    Column(Modifier.background(Soft).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DearbySegments(listOf("내 코드", "스캔"), mode, { mode = it })
        QrShareCard("김지민", "서비스 기획 · 커뮤니티", url, {}, errorMessage = errorMessage,
            activities = listOf(DearbyChoice("conference", "Dearby 개발자 컨퍼런스"), DearbyChoice("camp", "Dearby 메이커 캠프")),
            selectedActivityIds = selected, onToggleActivity = { id -> selected = if (id in selected) selected - id else selected + id })
    }
}

@Preview(name = "내 코드", widthDp = 390) @Composable private fun QrSharePreview() = DearbyTheme { QrShareSample() }
@Preview(name = "만드는 중", widthDp = 390) @Composable private fun QrLoadingPreview() = DearbyTheme { QrShareSample(url = null) }
