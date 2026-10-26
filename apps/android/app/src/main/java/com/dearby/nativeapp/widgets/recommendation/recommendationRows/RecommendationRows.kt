package com.dearby.nativeapp.widgets.recommendation.recommendationRows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 발견 맨 위의 `오늘의 맞춤 대기열` 한 줄 (2026-10-25 사용자 결정 "추천은 없애고 맞춤대기열 진입만 남겨줘": the 추천 rows are gone). [open] starts
 * the funnel first when it was never completed, else the queue; the caller decides. Not [ready] it says what comes first and [open] asks for it:
 * sign-in when signed out (contract "비로그인에서 만들기·추천을 막음", 2026-10-25), the consent when [signedIn] without it (contract "개인정보 동의").
 */
@Composable fun QueueEntryRow(ready: Boolean, signedIn: Boolean, open: () -> Unit) {
    ListItem(headlineContent = { Text("오늘의 맞춤 대기열", fontWeight = FontWeight.SemiBold) }, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = open),
        supportingContent = { Text(when { ready -> "위로 넘기며 나에게 맞는 활동을 둘러봐요"; signedIn -> "동의하면 나에게 맞는 활동을 골라 드려요"; else -> "로그인하면 나에게 맞는 활동을 골라 드려요" }) },
        leadingContent = { Icon(Icons.Rounded.AutoAwesomeMotion, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}

/** 프로필 `추천` 묶음 (contract): `관심 분야·활동 종류` reopens the funnel; `지원 자격 정보` shows [eligibilitySummary] once entered. */
@Composable fun RecommendationProfileGroup(eligibilitySummary: String?, preferences: () -> Unit, eligibility: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("추천", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        val chevron: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
        ListItem(headlineContent = { Text("지원 자격 정보") }, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = eligibility),
            supportingContent = { Text(eligibilitySummary ?: "입력하면 지원할 수 있는 활동만 추천해요") }, trailingContent = chevron, colors = colors)
        ListItem(headlineContent = { Text("관심 분야·활동 종류") }, Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = preferences), trailingContent = chevron, colors = colors)
    }
}
