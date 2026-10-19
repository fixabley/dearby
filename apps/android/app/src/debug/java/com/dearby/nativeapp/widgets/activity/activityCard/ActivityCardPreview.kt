package com.dearby.nativeapp.widgets.activity.activityCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyTheme

/** 미리보기 예시(contract "발견 카드 리뉴얼"): 모집 중·모집 예정은 `모집` 줄을 강조하고, 마감·판정 불가는 보조색 그대로다. */
@Composable fun ActivityCardSample() = Column(Modifier.background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ActivityCard("Dearby 개발자 컨퍼런스", "모집 중", true, "10월 20일 10:00 – 11월 10일 18:00", "2026년 11월 24일 – 2027년 2월 6일", "코엑스 그랜드볼룸", {})
    ActivityCard("SK AIGEN 1기", "모집 예정", true, "11월 1일 – 11월 15일", "2027년 3월 11일부터", "SK 서린빌딩", {})
    ActivityCard("청년 창업 아이디어 공모전", "모집 마감", false, "10월 1일 – 10월 12일", "2026년 10월 28일", null, {})
    ActivityCard("주말 데이터 분석 스터디", "모집 여부 확인 필요", false, null, "", "온라인", {})
}

@Preview(name = "활동 카드", widthDp = 390) @Composable private fun ActivityCardPreview() = DearbyTheme { ActivityCardSample() }
