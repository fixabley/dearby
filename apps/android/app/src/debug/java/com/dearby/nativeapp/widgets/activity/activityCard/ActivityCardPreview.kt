package com.dearby.nativeapp.widgets.activity.activityCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.R
import com.dearby.nativeapp.shared.ui.DearbyTheme
import java.time.Instant

/** 미리보기와 계측 캡처가 함께 쓰는 예시. 신청 주소는 실제가 아닌 example.invalid 값이다. */
@Composable fun ActivityCardSample(onApply: (String) -> Unit = {}) = Column(Modifier.background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ActivityCard("Dearby 개발자 컨퍼런스", "개발자와 기획자가 함께 만드는 하루", "모집 중 · 바로 신청", "10월 24일 · 서울", painterResource(R.drawable.prototype_conference), {},
        applyUrl = "https://example.invalid/apply", recruitmentEnd = Instant.parse("2026-10-20T14:59:00Z"), onApply = onApply)
    ActivityCard("Dearby 메이커 캠프", "선발형 · 지원서 검토 후 안내", "모집 중 · 선발형", "11월 7일 · 부산", painterResource(R.drawable.prototype_camp), {})
}

@Preview(name = "빠른 신청", widthDp = 390) @Composable private fun ActivityCardPreview() = DearbyTheme { ActivityCardSample() }
