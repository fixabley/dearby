package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** One search hit the picker shows. */
data class PickerItem(val id: Long, val name: String, val detail: String?)

/**
 * 한 단계의 선택 창: 등록된 활동 정보(조직·프로그램·활동·역할)를 검색해 고르거나 그대로 적는다. 직접 입력은 관리자 승인 전까지 명함에 못 넣는다.
 * 검색 요청·결과는 화면이 갖고, 이 위젯은 값과 콜백만 받는다. 검색 칸은 기본 크기 `OutlinedTextField`([PickerSearchField])다.
 */
@Composable fun ActivityInfoPicker(
    title: String, prompt: String, query: String, onQueryChange: (String) -> Unit, items: List<PickerItem>, loading: Boolean, failed: Boolean,
    pickedName: String?, typedName: String, onPick: (PickerItem) -> Unit, onClearPick: () -> Unit, onType: (String) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        if (pickedName != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.width(8.dp))
                    Text(pickedName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClearPick) { Text("바꾸기") }
                }
            }
            return@Column
        }
        PickerSearchField(query, onQueryChange, prompt)
        if (typedName.isNotEmpty()) OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(typedName, fontWeight = FontWeight.SemiBold)
                    Text("직접 입력 · 관리자 승인 뒤 명함에 넣을 수 있어요", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ onType("") }) { Text("지우기") }
            }
        }
        if (loading && items.isEmpty()) Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        else if (failed) Text("목록을 불러오지 못했어요. 직접 적어도 돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        items.forEach { item ->
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(item) }.padding(vertical = 8.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                item.detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
        val trimmed = query.trim()
        if (trimmed.isNotEmpty() && items.none { it.name == trimmed }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "목록에 없으면 적은 이름 그대로 저장해요") { onType(trimmed); onQueryChange("") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                Text("‘$trimmed’ 직접 입력", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        } else if (trimmed.isEmpty() && items.isEmpty() && !loading) {
            Text("이름으로 검색하거나 없으면 그대로 적어 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 선택 창의 검색 칸(contract "폼은 플랫폼 기본 컴포넌트", 2026-10-19 밤): Material 3 `OutlinedTextField` 기본 크기에 항상 보이는 [label], 돋보기, 검색어 지우기.
 * 접근성 이름은 라벨뿐이다(따로 `contentDescription`을 두지 않는다).
 */
@Composable internal fun PickerSearchField(query: String, onQueryChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String? = null) = OutlinedTextField(
    query, onQueryChange, modifier.fillMaxWidth(), label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } }, singleLine = true,
    leadingIcon = { Icon(Icons.Rounded.Search, null) },
    trailingIcon = if (query.isNotEmpty()) ({ IconButton({ onQueryChange("") }) { Icon(Icons.Rounded.Cancel, "검색어 지우기") } }) else null,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
