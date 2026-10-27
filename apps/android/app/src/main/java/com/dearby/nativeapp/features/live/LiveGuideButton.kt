package com.dearby.nativeapp.features.live

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 행사 안내 시작 (2026-10-27 "네이버지도의 길찾기 안내 시작처럼"): on the 발견 today card and atop 내 시간표 of the timetable sheets while the day has
 * 찜 sessions. [guiding] (this activity's guide runs) shows the `안내 중 · 종료` chip in its place; a tap on it is [stop]. [highlight] (오늘 행사
 * 온보딩's 내 시간표) makes the start a filled button.
 */
@Composable fun LiveGuideButton(guiding: Boolean, start: () -> Unit, stop: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false) {
    if (guiding) AssistChip(stop, { Text("안내 중 · 종료") }, modifier,
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(AssistChipDefaults.IconSize), tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(AssistChipDefaults.IconSize)) })
    else {
        val content: @Composable RowScope.() -> Unit = {
            Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("행사 안내 시작")
        }
        if (highlight) Button(start, modifier, content = content) else FilledTonalButton(start, modifier, content = content)
    }
}
