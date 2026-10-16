package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** One search hit the picker shows. */
data class PickerItem(val id: Long, val name: String, val detail: String?)

/**
 * 한 단계의 선택 창: 등록된 활동 정보(조직·프로그램·활동·역할)를 검색해 고르거나 그대로 적는다. 직접 입력은 관리자 승인 전까지 명함에 못 넣는다.
 * 검색 요청·결과는 화면이 갖고, 이 위젯은 값과 콜백만 받는다.
 */
@Composable fun ActivityInfoPicker(
    title: String, prompt: String, query: String, onQueryChange: (String) -> Unit, items: List<PickerItem>, loading: Boolean, failed: Boolean,
    pickedName: String?, typedName: String, onPick: (PickerItem) -> Unit, onClearPick: () -> Unit, onType: (String) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        if (pickedName != null) {
            Surface(color = Mint, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Teal); Spacer(Modifier.width(8.dp))
                    Text(pickedName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClearPick) { Text("바꾸기") }
                }
            }
            return@Column
        }
        DearbySearchField(query, onQueryChange, prompt)
        if (typedName.isNotEmpty()) OutlinedCard(border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(typedName, fontWeight = FontWeight.SemiBold)
                    Text("직접 입력 · 관리자 승인 뒤 명함에 넣을 수 있어요", color = Quiet, style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ onType("") }) { Text("지우기") }
            }
        }
        if (loading && items.isEmpty()) Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
        else if (failed) Text("목록을 불러오지 못했어요. 직접 적어도 돼요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
        items.forEach { item ->
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onPick(item) }.padding(vertical = 8.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                item.detail?.let { Text(it, color = Quiet, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(color = Line)
        }
        val trimmed = query.trim()
        if (trimmed.isNotEmpty() && items.none { it.name == trimmed }) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "목록에 없으면 적은 이름 그대로 저장해요") { onType(trimmed); onQueryChange("") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Edit, null, tint = Teal); Spacer(Modifier.width(8.dp))
                Text("‘$trimmed’ 직접 입력", color = Teal, fontWeight = FontWeight.SemiBold)
            }
        } else if (trimmed.isEmpty() && items.isEmpty() && !loading) {
            Text("이름으로 검색하거나 없으면 그대로 적어 주세요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
