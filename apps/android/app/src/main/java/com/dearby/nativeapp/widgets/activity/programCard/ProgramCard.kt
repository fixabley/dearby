package com.dearby.nativeapp.widgets.activity.programCard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.dearby.nativeapp.shared.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))
private val monthFormat = DateTimeFormatter.ofPattern("yyyy.M", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"))

/**
 * 발견 목록의 프로그램 카드(contract "발견 카드 리뉴얼", One UI 알림 카드 느낌을 Material 3로): `OutlinedCard`(모서리 20, 1dp 선, 그림자 없음). 왼쪽 44dp
 * 로고 자리는 [logoUrl]을 Coil `AsyncImage`로 꽉 채워(Crop, 모서리 14) 보이고 로딩 중·실패·null은 프로그램 이름 첫 글자 아바타다(contract "프로그램 로고"). 가운데 이름(굵게) → 조직 → 분야 1개([fieldTags]의 첫째)와 성격 [traitTags]의
 * 아이콘+글자 칩 한 줄(넘치면 `+n`, 없으면 생략) → 검색이 활동 이름에 맞으면 그 아래 줄에 `맞는 활동 n개`([matchedCount]; contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음"). 오른쪽 위 `Badge`는
 * 활동 수([statusCounts] 합, 없으면 [openCount] + [upcomingCount]), 그 아래 한 줄은 모집 중이 있으면 `M월 d일 마감`([deadline], 모집 마감이 없는 활동 날짜면 `M월 d일 행사`), 예정만 있으면 `M월 d일 시작`([nearestStart]),
 * 둘 다 없으면 진행 중이 있으면 `진행 중 n개`([statusCounts]), 아니면 `최근 yyyy.M`([latestActivityAt]). 머리를 누르면 [onToggle]; 펼쳐지면 배지 자리가 `IconButton`(위쪽 화살표, 44dp, `접기`)으로 바뀌고 [content]가
 * 카드 안에 펼쳐진다. 높이는 스프링(`animateContentSize`), 내용은 `expandVertically + fadeIn`(FastOutSlowIn), 화살표는 180° 회전; 애니메이터 배율 0에서는 즉시 바뀐다.
 * 접근성에서는 머리가 `펼침`/`접힘` 상태를 가진 버튼으로 읽히고 칩 줄의 태그 이름은 머리 라벨에 묶인다.
 */
@Composable fun ProgramCard(
    title: String, organization: String, openCount: Int, upcomingCount: Int, deadline: Instant?, nearestStart: Instant?, latestActivityAt: Instant?,
    fieldTags: List<TagLabel>, traitTags: List<TagLabel>, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, logoUrl: String? = null, matchedCount: Int = 0,
    /** `새 모집` badge beside the title (즐겨찾기, contract "즐겨찾기(하트)·새 모집 푸시 알림"). */
    newRecruitment: Boolean = false,
    /** [deadline] is an activity date, not a recruitment deadline: `M월 d일 행사` instead of `마감`. */
    deadlineIsEvent: Boolean = false,
    /**
     * 발견 `이벤트` (2026-10-26, same as iOS #398): the line shows the event date instead of the deadline. The summary's dates are per kind, so an
     * open event whose nearest date is its start ([deadlineIsEvent]) shows [deadline] as `M월 d일 행사`; otherwise [latestActivityAt] (the kind's
     * most recent activity start) as `M월 d일 행사` while it is ahead, else `최근 yyyy.M`.
     */
    eventDates: Boolean = false,
    /** An open or upcoming activity has a 트랙·세션 시간표 (`ProgramSummary.hasTimetable`): a `시간표` line under the tags, as on the activity card. */
    timetable: Boolean = false,
    /**
     * `statusCounts` in the asked states (contract "활동 상태", 2026-10-26): the badge counts all of them, and a program with only 진행 중 or
     * past activities says `진행 중 n개` (else `최근 yyyy.M`). Empty (older API, 즐겨찾기) keeps 모집 중 + 모집 예정.
     */
    statusCounts: Map<String, Int> = emptyMap(),
    /** The program heart (and its bell) left of the count badge, with their own taps and names. */
    actions: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val sizeSpec: FiniteAnimationSpec<IntSize> = if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    val fadeSpec: FiniteAnimationSpec<Float> = if (reduceMotion) snap() else tween(220, easing = FastOutSlowInEasing)
    // Hoisted so the chevron rotates in from 0° when it replaces the badge; `animateFloatAsState` would start at its target.
    val rotation = remember { Animatable(if (expanded) 180f else 0f) }
    LaunchedEffect(expanded) { rotation.animateTo(if (expanded) 180f else 0f, if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    val count = if (statusCounts.isEmpty()) openCount + upcomingCount else statusCounts.values.sum()
    val inProgress = statusCounts["IN_PROGRESS"] ?: 0
    val meta = when {
        eventDates && openCount > 0 && deadlineIsEvent && deadline != null -> dayFormat.format(deadline) + " 행사"
        eventDates -> latestActivityAt?.let { if (it > Instant.now()) dayFormat.format(it) + " 행사" else "최근 " + monthFormat.format(it) }
        openCount > 0 && deadline != null -> dayFormat.format(deadline) + if (deadlineIsEvent) " 행사" else " 마감"
        openCount == 0 && upcomingCount > 0 && nearestStart != null -> dayFormat.format(nearestStart) + " 시작"
        openCount == 0 && upcomingCount == 0 && inProgress > 0 -> "진행 중 ${inProgress}개"
        openCount == 0 && upcomingCount == 0 && latestActivityAt != null -> "최근 " + monthFormat.format(latestActivityAt)
        else -> null
    }
    OutlinedCard(modifier.fillMaxWidth().animateContentSize(sizeSpec), shape = RoundedCornerShape(20.dp), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        // Only the head toggles, so the activity cards below keep their own accessibility nodes.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onToggle).semantics { stateDescription = if (expanded) "펼침" else "접힘" }
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
        ) {
            ProgramLogo(logoUrl, title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (newRecruitment) Badge(containerColor = TealFill, contentColor = Color.White) { Text("새 모집", Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
                Text(organization, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TagChipRow(fieldTags.firstOrNull(), traitTags, Modifier.padding(top = 4.dp))
                if (timetable) TimetableMark(Modifier.padding(top = 2.dp))
                if (matchedCount > 0) Text("맞는 활동 ${matchedCount}개", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    actions?.invoke()
                    if (expanded) IconButton(onToggle, Modifier.size(44.dp).semantics { contentDescription = "접기" }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation.value))
                    } else Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Badge(Modifier.size(32.dp).semantics { contentDescription = "활동 ${count}개" }, if (count > 0) TealFill else MaterialTheme.colorScheme.surfaceContainer, if (count > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) {
                            Text(count.toString(), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (meta != null) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically(sizeSpec) + fadeIn(fadeSpec), exit = shrinkVertically(sizeSpec) + fadeOut(fadeSpec)) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/**
 * The 44dp logo slot: the logo image once Coil has it, the initial avatar until then and on failure or without a logo. The avatar is not
 * layered under the image, so a logo with transparent parts shows the card surface, not the mint tile. Decorative: the title says the same.
 */
@Composable private fun ProgramLogo(url: String?, title: String) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(Modifier.size(44.dp).clearAndSetSemantics {}) {
        if (!loaded) InitialAvatar(title, 44.dp, shape = RoundedCornerShape(14.dp))
        if (url != null) AsyncImage(url, null, Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop,
            onState = { loaded = it is AsyncImagePainter.State.Success })
    }
}
