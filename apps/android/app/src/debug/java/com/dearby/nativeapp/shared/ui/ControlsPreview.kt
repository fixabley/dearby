package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 미리보기와 계측 캡처가 함께 쓰는 공통 입력 컴포넌트 예시 배치. 화면 이동이나 예시 데이터 상태는 담지 않는다. */
@Composable fun DearbyControlsGallery(editing: Boolean = true) {
    var segment by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf("conference")) }
    var name by remember { mutableStateOf("김지민") }
    var introduction by remember { mutableStateOf("") }
    Column(Modifier.background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DearbySegments(listOf("저장한 활동", "신청한 활동"), segment, { segment = it })
        DearbySearchField(query, { query = it }, "이름, 직무, 활동으로 검색")
        DearbySectionHeader("Dearby 개발자 컨퍼런스", 3)
        DearbyChoiceChips(listOf(DearbyChoice("conference", "Dearby 개발자 컨퍼런스"), DearbyChoice("camp", "Dearby 메이커 캠프"), DearbyChoice("meetup", "Dearby 커뮤니티 밋업")),
            chosen, { id -> chosen = if (id in chosen) chosen - id else chosen + id }, "함께 보낼 활동")
        DearbyTogetherActivityLabel("Dearby 개발자 컨퍼런스")
        DearbyTogetherActivityLabel("Dearby 메이커 캠프 여름 시즌 집중 프로그램", otherCount = 2)
        DearbyInlineField("이름", name, { name = it }, editing, style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold))
        DearbyInlineField("소개", introduction, { introduction = it }, editing, placeholder = "한 줄 소개를 적어 주세요", singleLine = false)
    }
}

@Preview(name = "편집", widthDp = 390) @Composable private fun EditingPreview() = DearbyTheme { DearbyControlsGallery() }
@Preview(name = "읽기", widthDp = 390) @Composable private fun ReadingPreview() = DearbyTheme { DearbyControlsGallery(editing = false) }
