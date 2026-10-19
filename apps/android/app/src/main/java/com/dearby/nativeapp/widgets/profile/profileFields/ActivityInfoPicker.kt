package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** One search hit the picker shows. */
data class PickerItem(val id: Long, val name: String, val detail: String?)

/**
 * 한 단계의 선택 창: 등록된 활동 정보(조직·프로그램·활동·역할)를 검색해 고르거나 그대로 적는다. 직접 입력은 관리자 승인 전까지 명함에 못 넣는다.
 * 검색 요청·결과는 화면이 갖고, 이 위젯은 값과 콜백만 받는다. 검색은 발견 탭과 같은 `DearbySearchField`, 결과는 행 전체가 눌리는 `ListItem`, 결과 없음은 빈 상태,
 * 맨 아래 `"<검색어>" 직접 입력` 행이다(contract "퍼널 검색은 발견 검색과 같은 경험", 2026-10-20). 고른 항목은 `ListItem`(확인 아이콘 + `바꾸기`)로 보인다.
 */
@Composable fun ActivityInfoPicker(
    title: String, prompt: String, query: String, onQueryChange: (String) -> Unit, items: List<PickerItem>, loading: Boolean, failed: Boolean,
    pickedName: String?, typedName: String, onPick: (PickerItem) -> Unit, onClearPick: () -> Unit, onType: (String) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        if (pickedName != null) {
            ListItem({ Text(pickedName) }, leadingContent = { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { TextButton(onClearPick) { Text("바꾸기") } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer))
            return@Column
        }
        DearbySearchField(query, onQueryChange, prompt)
        if (typedName.isNotEmpty()) ListItem({ Text(typedName) }, supportingContent = { Text("직접 입력 · 관리자 승인 뒤 명함에 넣을 수 있어요") },
            leadingContent = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, trailingContent = { TextButton({ onType("") }) { Text("지우기") } },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer))
        PickerResults(query, items, loading, failed, "이름으로 검색하거나 없으면 그대로 적어 주세요.", onPick) { onType(it); onQueryChange("") }
    }
}

/**
 * The shared result list of both pickers: full-row `ListItem`s, a loading row, the failure note, a `CenteredMessage`-style empty state when a query finds
 * nothing, and the `"<검색어>" 직접 입력` row at the bottom whenever the typed name is not among the hits.
 */
@Composable internal fun PickerResults(query: String, items: List<PickerItem>, loading: Boolean, failed: Boolean, idleHint: String, onPick: (PickerItem) -> Unit, onType: (String) -> Unit) {
    val trimmed = query.trim()
    when {
        loading && items.isEmpty() -> Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        failed -> Text("목록을 불러오지 못했어요. 직접 적어도 돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        trimmed.isNotEmpty() && items.isEmpty() -> DearbyEmptyState("검색 결과가 없어요", "아래에서 적은 이름 그대로 저장할 수 있어요.", icon = Icons.Rounded.Search)
        trimmed.isEmpty() && items.isEmpty() -> Text(idleHint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
    items.forEach { item ->
        ListItem({ Text(item.name) }, Modifier.clickable(role = Role.Button) { onPick(item) }, supportingContent = item.detail?.let { { Text(it) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
    if (trimmed.isNotEmpty() && items.none { it.name == trimmed }) {
        ListItem({ Text("‘$trimmed’ 직접 입력", color = MaterialTheme.colorScheme.primary) }, Modifier.clickable(role = Role.Button, onClickLabel = "목록에 없으면 적은 이름 그대로 저장해요") { onType(trimmed) },
            leadingContent = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}
