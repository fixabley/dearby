package com.dearby.nativeapp.widgets.activity.activityCard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val deadlineFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

/**
 * 발견 목록의 활동 카드. 본문을 누르면 [open](상세 이동)을 부른다.
 * [applyUrl]을 주면 카드 아래에 마감일 한 줄과 공식 신청 바로가기를 붙이고, 누르면 [onApply]로 화면이 외부 브라우저를 연다.
 * 보일지 여부는 화면이 카탈로그 값으로 정한다.
 */
@Composable fun ActivityCard(
    title: String, summary: String, status: String, dateAndPlace: String, artwork: Painter, open: () -> Unit,
    modifier: Modifier = Modifier, applyUrl: String? = null, recruitmentEnd: Instant? = null, onApply: (String) -> Unit = {},
) {
    Surface(modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "상세 보기", onClick = open).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(artwork, "활동 소개용 예시 사진", Modifier.size(112.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(summary, color = Quiet, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    MetaLine(Icons.Outlined.CalendarToday, status)
                    MetaLine(Icons.Outlined.LocationOn, dateAndPlace)
                }
            }
            if (applyUrl != null) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(recruitmentEnd?.let { deadlineFormat.format(it) + " 마감" } ?: "마감일 미확인", Modifier.weight(1f), color = Quiet, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({ onApply(applyUrl) }, Modifier.heightIn(min = 44.dp).semantics { contentDescription = "$title 공식 사이트에서 신청, 외부 브라우저로 열려요" },
                        shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, Teal), colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("공식 사이트에서 신청", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable private fun MetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) = Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(15.dp), tint = Quiet)
    Text(text, color = Quiet, maxLines = 2, style = MaterialTheme.typography.bodySmall)
}
