package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** One 직무 chip: a picked job, or a free-typed name waiting for ([pending]) or refused by ([rejected]) admin approval. */
data class JobTag(val id: String, val title: String, val pending: Boolean, val rejected: Boolean)

/**
 * 직무 tag picker (contract "직무"): chips for the chosen jobs, then a search over the job tree; a name that is not in the
 * tree can be typed and waits for admin approval. Search requests and results belong to the screen; this widget only draws.
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
                    label = { Text(tag.title, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = if (tag.pending) ({ Icon(Icons.Outlined.Schedule, "승인 대기", Modifier.size(18.dp)) }) else null,
                    trailingIcon = { Icon(Icons.Outlined.Close, "삭제", Modifier.size(18.dp)) },
                    colors = InputChipDefaults.inputChipColors(selectedContainerColor = Mint, selectedLabelColor = Teal, selectedLeadingIconColor = Teal, selectedTrailingIconColor = Teal),
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
        }
        if (tags.any { it.pending }) Text("직접 적은 직무는 관리자 승인 뒤 명함에 들어가요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
        if (full) { Text("직무는 ${tags.size}개까지 고를 수 있어요.", color = Quiet, style = MaterialTheme.typography.bodySmall); return@Column }
        DearbySearchField(query, onQueryChange, "직무 검색 (예: 백엔드, 디자인)")
        if (loading && items.isEmpty()) Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
        else if (failed) Text("목록을 불러오지 못했어요. 직접 적어도 돼요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
        items.forEach { item ->
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(item) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                item.detail?.let { Text(it, color = Quiet, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(color = Line)
        }
        val trimmed = query.trim()
        if (trimmed.isNotEmpty() && items.none { it.name == trimmed }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "목록에 없으면 적은 이름 그대로 저장해요") { onType(trimmed); onQueryChange("") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, null, tint = Teal); Spacer(Modifier.width(8.dp))
                Text("‘$trimmed’ 직접 입력", color = Teal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            }
        } else if (trimmed.isEmpty() && items.isEmpty() && !loading) {
            Text("이름으로 검색해 여러 개 고르거나, 없으면 그대로 적어 주세요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
