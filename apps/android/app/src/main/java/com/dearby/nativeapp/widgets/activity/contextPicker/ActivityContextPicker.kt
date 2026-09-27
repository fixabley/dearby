package com.dearby.nativeapp.widgets.activity.contextPicker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.Field

data class ActivityChoiceState(val id: String, val title: String)

@Composable fun ActivityContextPicker(choices: List<ActivityChoiceState>, activityId: String?, label: String, change: (String?, String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var direct by rememberSaveable { mutableStateOf(label.isNotEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("교환한 활동 (선택 사항)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ picking = true }) { Text("등록 활동") }
            TextButton({ direct = true; change(null, "") }) { Text("직접 입력") }
            TextButton({ direct = false; change(null, "") }) { Text("선택 안 함") }
        }
        if (activityId != null) Text(choices.find { it.id == activityId }?.title ?: "활동 정보 확인 필요")
        else if (direct) Field("활동 이름", label, { change(null, it.take(200)) })
        else Text("활동 선택 안 함", style = MaterialTheme.typography.bodySmall)
        Text("과거 활동도 선택할 수 있어요. 선택은 참가 인증이 아닙니다.", style = MaterialTheme.typography.bodySmall)
    }
    if (picking) AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = { picking = false }, title = { Text("등록 활동 선택") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            if (choices.isEmpty()) item { Text("등록 활동을 불러오지 못했거나 아직 없습니다. 발견에서 새로고침해 주세요.") }
            items(choices, key = { it.id }) { item -> TextButton({ direct = false; change(item.id, ""); picking = false }, Modifier.fillMaxWidth()) { Text(item.title) } }
        }
    }, confirmButton = { TextButton({ picking = false }) { Text("닫기") } })
}
