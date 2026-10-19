package com.dearby.nativeapp.widgets.activity.programHeader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val deadlineFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))
private val monthFormat = DateTimeFormatter.ofPattern("yyyy.M", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

/**
 * 발견 목록의 프로그램 머리글(contract "발견 프로그램 묶음"): 이름·조직·열린 활동 수·가장 가까운 마감. 열린 활동이 없으면(모집 중만 보기 끔) 가장 최근 활동
 * 시작 달 [latestActivityAt]을 "최근 활동 yyyy.M"으로 보인다. 누르면 [onToggle]로 화면이 펼치거나 접는다.
 * 접근성에서는 `펼침`/`접힘` 상태를 가진 버튼으로 읽히고, 화살표 회전은 동작 줄이기에서 바로 바뀐다. 펼친 활동 목록은 화면이 아래에 둔다.
 */
@Composable fun ProgramHeaderRow(
    title: String, organization: String, openCount: Int, deadline: Instant?, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier,
    latestActivityAt: Instant? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, if (reduceMotion) snap() else spring(), label = "chevron")
    Surface(onClick = onToggle, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { role = Role.Button; stateDescription = if (expanded) "펼침" else "접힘" },
        shape = RoundedCornerShape(12.dp), color = if (expanded) Mint else Color.White, border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(organization, color = Quiet, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (openCount > 0) Text("모집 중 ${openCount}개" + (deadline?.let { " · " + deadlineFormat.format(it) + " 마감" } ?: ""), color = Teal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                else if (latestActivityAt != null) Text("최근 활동 " + monthFormat.format(latestActivityAt), color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.rotate(rotation), tint = Quiet)
        }
    }
}
