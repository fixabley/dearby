package com.dearby.nativeapp.shared.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 내용을 불러오는 동안의 스켈레톤(contract "내용 로딩은 스켈레톤", 2026-10-22). 사용자 동작의 진행·풀투 리프레시·목록 끝 다음 페이지는 스피너 그대로다.

/** 자리 표시 블록 하나: Material 3 `surfaceContainerHighest` 둥근 사각형. 크기는 [modifier]가 정한다. */
@Composable fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) =
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, shape))

/** 글자 한 줄 자리: 너비의 [fraction], 높이 [height]. */
@Composable fun SkeletonLine(fraction: Float, height: Dp = 14.dp) = SkeletonBlock(Modifier.fillMaxWidth(fraction).height(height))

/**
 * 스켈레톤 묶음: 화면 읽기 도구에는 `불러오는 중` 하나로만 읽히고(자리 표시는 읽히지 않는다), 묶음 전체가 은은하게 맥박친다.
 * 동작 줄이기(애니메이터 배율 0)에서는 멈춘다. 블록은 실제 내용이 놓일 자리에 위에서부터 둔다(가운데 정렬하지 않는다).
 */
@Composable fun DearbySkeleton(modifier: Modifier = Modifier, spacing: Dp = 10.dp, content: @Composable ColumnScope.() -> Unit) {
    val alpha = if (rememberReduceMotion()) 1f
        else rememberInfiniteTransition(label = "skeleton").animateFloat(1f, .55f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeleton").value
    Column(modifier.graphicsLayer { this.alpha = alpha }.clearAndSetSemantics { contentDescription = "불러오는 중"; liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/** `ListItem` 모양의 목록 행 [count]개: 앞 [leading] 원(0이면 없음)과 두 줄. 받은 명함·내 활동·설정·변경 이력·고르기 결과에 쓴다. */
@Composable fun SkeletonRows(count: Int, leading: Dp = 0.dp) = repeat(count) { index ->
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (leading > 0.dp) SkeletonBlock(Modifier.size(leading), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(if (index % 2 == 0) .45f else .6f, 16.dp)
            SkeletonLine(if (index % 2 == 0) .7f else .5f, 12.dp)
        }
    }
}

/** 명함 한 장 모양(contract "명함 표현": 가운데 아바타 56·이름·직무, 소개 줄, 연락처 행). 명함·프로필·버전 상세·명함 만들기에 쓴다. */
@Composable fun SkeletonCardBody(modifier: Modifier = Modifier) =
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(Modifier.size(56.dp), CircleShape)
                SkeletonLine(.35f, 20.dp)
                SkeletonLine(.5f)
            }
            Spacer(Modifier.height(4.dp))
            SkeletonLine(.9f)
            SkeletonLine(.75f)
            Spacer(Modifier.height(4.dp))
            SkeletonLine(.25f, 16.dp)
            repeat(2) { SkeletonLine(.6f) }
        }
    }
