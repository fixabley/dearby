package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** 변경 이력: every saved profile version, newest first. */
@Composable fun ProfileVersionsPage(state: VersionsState, open: (Int) -> Unit, more: () -> Unit, close: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("변경 이력", close)
        state.error?.let { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) }
        if (!state.loading && state.items.isEmpty() && state.error == null) Text("아직 저장한 버전이 없어요.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(state.items, key = { it.first }) { (version, createdAt) ->
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { open(version) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text("버전 $version", style = MaterialTheme.typography.titleMedium)
                    Text(formatWhen(createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            if (state.nextCursor != null) item { TextButton(more, Modifier.fillMaxWidth()) { Text("더 보기") } }
            if (state.loading) item { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) } }
        }
    }
}

/** One version's content with "이 버전으로 되돌리기" (a new version is saved; the history stays). */
@Composable fun ProfileVersionPage(state: VersionsState, version: Int, restore: () -> Unit, close: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("버전 $version", close)
        val detail = state.detail
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                detail != null -> {
                    Text(formatWhen(detail.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    PersonHeader(detail.name.ifBlank { "이름 없음" }, detail.job, detail.introduction, filled = true)
                    HorizontalDivider()
                    Text("연락처", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    detail.contacts.forEach { (label, value) -> InfoRow(label, value) }
                    HorizontalDivider()
                    Text("활동 이력", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    detail.histories.forEachIndexed { index, h -> TimelineEntry(h.detail, h.title, if (h.verified) "확인됨" else "미확인", index == detail.histories.lastIndex) }
                }
                state.detailError != null -> Text(state.detailError, color = MaterialTheme.colorScheme.error)
                else -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            }
        }
        if (detail != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                DearbyButton(restore, Modifier.fillMaxWidth(), enabled = !state.restoring) {
                    if (state.restoring) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp) else Text("이 버전으로 되돌리기")
                }
                Text("되돌리면 새 버전으로 저장되고 이력은 남아요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatWhen(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy. M. d. HH:mm"))
}.getOrDefault(iso)
