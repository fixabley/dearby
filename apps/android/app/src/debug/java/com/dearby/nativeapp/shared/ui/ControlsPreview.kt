package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 미리보기와 계측 캡처가 함께 쓰는 공통 입력 컴포넌트 예시 배치. 화면 이동이나 예시 데이터 상태는 담지 않는다. */
@Composable fun DearbyControlsGallery(editing: Boolean = true) {
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("김지민") }
    var introduction by remember { mutableStateOf("") }
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DearbySearchField(query, { query = it }, "이름, 직무, 활동으로 검색")
        DearbySearchField("", {}, "이름, 직무, 활동으로 검색", expansion = 0f)
        DearbySearchField("", {}, "이름, 직무, 활동으로 검색", expansion = .7f)
        DearbyInlineField("이름", name, { name = it }, editing, style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold))
        DearbyInlineField("소개", introduction, { introduction = it }, editing, placeholder = "한 줄 소개를 적어 주세요", singleLine = false)
    }
}

@Preview(name = "편집", widthDp = 390) @Composable private fun EditingPreview() = DearbyTheme { DearbyControlsGallery() }
@Preview(name = "읽기", widthDp = 390) @Composable private fun ReadingPreview() = DearbyTheme { DearbyControlsGallery(editing = false) }
