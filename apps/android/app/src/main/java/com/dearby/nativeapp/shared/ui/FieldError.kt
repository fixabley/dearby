package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 오류 글자·테두리. 웹 `--danger`와 같은 값. */
val Danger = Color(0xFFA12A2A)

/** 입력 칸 바로 아래에 붙는 오류 문구. 색만이 아니라 아이콘으로도 오류임을 보인다. */
@Composable fun DearbyFieldError(text: String, modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = Danger)
    Text(text, color = Danger, style = MaterialTheme.typography.bodyMedium)
}
