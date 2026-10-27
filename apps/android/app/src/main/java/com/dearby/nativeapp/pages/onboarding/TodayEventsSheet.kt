package com.dearby.nativeapp.pages.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.dearby.nativeapp.shared.ui.InitialAvatar

/** One of today's events (2026-10-27 "오늘 행사 온보딩"): [time] is today's range (`10:00 ~ 17:30`, `종일`), [place] null without a venue. */
data class TodayEventChoice(val id: String, val name: String, val program: String, val logoUrl: String?, val place: String?, val time: String)

/**
 * 오늘 행사 온보딩 (2026-10-27 사용자 "오늘 진행 중인 행사 중 참여 중인 게 있는지 물어보는 온보딩"): a Material 3 `ModalBottomSheet` asking
 * `오늘 참여하는 행사가 있나요?` over today's events (at most 5, each a checkbox row: logo, name, place, today's range). `참여해요` ([join], enabled once
 * one is chosen) hands back the chosen ids in list order; `오늘은 아니에요` and a swipe down are [skip].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TodayEventsSheet(events: List<TodayEventChoice>, join: (List<String>) -> Unit, skip: () -> Unit) {
    // A single event starts checked: the question is then only yes or no.
    var chosen by remember(events) { mutableStateOf(if (events.size == 1) setOf(events.first().id) else emptySet()) }
    ModalBottomSheet(skip, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("오늘 참여하는 행사가 있나요?", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                Text("고르면 내 활동에 넣고 오늘 시간표를 열어 드려요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                events.forEach { event ->
                    val on = event.id in chosen
                    ListItem(
                        headlineContent = { Text(event.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(listOfNotNull(event.place, event.time).joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { EventLogo(event.logoUrl, event.program) },
                        trailingContent = { Checkbox(on, null) },
                        modifier = Modifier.toggleable(on, role = Role.Checkbox) { chosen = if (it) chosen + event.id else chosen - event.id },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button({ join(events.map { it.id }.filter { it in chosen }) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = chosen.isNotEmpty()) { Text("참여해요") }
                TextButton(skip, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("오늘은 아니에요") }
            }
        }
    }
}

/** The program logo (contract "프로그램 로고": rounded square, aspect fill), the program's initial while loading, failed or missing. */
@Composable private fun EventLogo(url: String?, program: String) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(Modifier.size(40.dp).clearAndSetSemantics {}) {
        if (!loaded) InitialAvatar(program, 40.dp, shape = RoundedCornerShape(12.dp))
        if (url != null) AsyncImage(url, null, Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop,
            onState = { loaded = it is AsyncImagePainter.State.Success })
    }
}
