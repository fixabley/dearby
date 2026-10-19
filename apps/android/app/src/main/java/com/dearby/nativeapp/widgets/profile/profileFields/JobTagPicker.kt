package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** One 직무 chip: a picked job, or a free-typed name waiting for ([pending]) or refused by ([rejected]) admin approval. */
data class JobTag(val id: String, val title: String, val pending: Boolean, val rejected: Boolean)

/**
 * 직무 tag picker (contract "직무"): chips for the chosen jobs, then the same search as discovery (`DearbySearchField`, full-row result `ListItem`s,
 * empty state, `"<검색어>" 직접 입력` row — contract "퍼널 검색은 발견 검색과 같은 경험", 2026-10-20) over the job tree; a name that is not in the tree waits for
 * admin approval. Search requests and results belong to the screen; this widget only draws.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun JobTagPicker(
    tags: List<JobTag>, query: String, onQueryChange: (String) -> Unit, items: List<PickerItem>, loading: Boolean, failed: Boolean,
    onPick: (PickerItem) -> Unit, onType: (String) -> Unit, onRemove: (String) -> Unit, modifier: Modifier = Modifier, full: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = !tag.rejected, onClick = { onRemove(tag.id) },
                    label = { Text(tag.title) },
                    leadingIcon = if (tag.pending) ({ Icon(Icons.Rounded.Schedule, "승인 대기", Modifier.size(18.dp)) }) else null,
                    trailingIcon = { Icon(Icons.Rounded.Cancel, "삭제", Modifier.size(18.dp)) },
                    colors = InputChipDefaults.inputChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer),
                )
            }
        }
        if (tags.any { it.pending }) Text("직접 적은 직무는 관리자 승인 뒤 명함에 들어가요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (full) { Text("직무는 ${tags.size}개까지 고를 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium); return@Column }
        DearbySearchField(query, onQueryChange, "직무 검색 (예: 백엔드, 디자인)")
        PickerResults(query, items, loading, failed, "이름으로 검색해 여러 개 고르거나, 없으면 그대로 적어 주세요.", onPick) { onType(it); onQueryChange("") }
    }
}
