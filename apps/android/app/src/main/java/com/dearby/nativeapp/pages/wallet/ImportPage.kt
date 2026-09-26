package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.dearby.nativeapp.shared.ui.FormColumn

data class ImportEntryState(val id: String, val name: String, val job: String, val context: String)
@Composable fun ImportPage(entries: List<ImportEntryState>, busy: Boolean, import: (Set<String>) -> Unit, later: () -> Unit) {
    var selected by remember(entries.map { it.id }) { mutableStateOf(emptySet<String>()) }
    FormColumn {
        Text("기기에 저장한 명함 가져오기", style = MaterialTheme.typography.headlineSmall)
        Text("Dearby에 저장한 명함입니다. 휴대폰 주소록은 가져오지 않습니다.")
        Row { TextButton({ selected = entries.map { it.id }.toSet() }) { Text("전체 선택") }; TextButton({ selected = emptySet() }) { Text("선택 해제") } }
        entries.forEach { entry -> Row { Checkbox(entry.id in selected, { checked -> selected = if (checked) selected + entry.id else selected - entry.id }); Column { Text(entry.name); Text(entry.job); Text(entry.context.ifBlank { "활동 선택 안 함" }) } } }
        Button({ import(selected) }, enabled = !busy && selected.isNotEmpty()) { Text("선택한 명함 ${selected.size}개 가져오기") }
        TextButton(later, enabled = !busy) { Text("나중에") }
        Text("가져오기에 실패하거나 선택하지 않은 명함은 기기에 보존됩니다.")
    }
}
