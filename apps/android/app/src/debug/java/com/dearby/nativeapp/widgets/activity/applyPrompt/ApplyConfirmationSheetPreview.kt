package com.dearby.nativeapp.widgets.activity.applyPrompt

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.dearby.nativeapp.shared.ui.DearbyTheme

@Preview(name = "신청 확인", widthDp = 390) @Composable private fun ApplyConfirmationPreview() = DearbyTheme {
    ApplyConfirmationSheet("주말 데이터 분석 스터디", {}, {}, {}, Modifier.background(Color.White))
}
