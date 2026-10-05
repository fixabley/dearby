package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class DearbyChoice(val id: String, val title: String)

@Composable fun DearbySegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Soft, RoundedCornerShape(11.dp)).selectableGroup()) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(Modifier.weight(1f).fillMaxHeight().heightIn(min = 44.dp).background(if (active) Teal else Color.Transparent, RoundedCornerShape(11.dp))
                .selectable(active, role = Role.Tab) { onSelect(index) }.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (active) Color.White else Quiet, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable fun DearbySectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { heading(); contentDescription = "$title, ${count}개" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
        Text("$count", Modifier.background(Mint, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp), color = Teal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable fun DearbySearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) = TextField(
    query, onQueryChange, modifier.fillMaxWidth().semantics { contentDescription = placeholder }, placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Quiet) },
    trailingIcon = { if (query.isNotEmpty()) IconButton({ onQueryChange("") }) { Icon(Icons.Outlined.Cancel, "검색어 지우기", tint = Quiet) } },
    singleLine = true, shape = RoundedCornerShape(50),
    colors = TextFieldDefaults.colors(focusedContainerColor = Soft, unfocusedContainerColor = Soft, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DearbyChoiceChips(items: List<DearbyChoice>, selected: Set<String>, onToggle: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val active = item.id in selected
            FilterChip(active, { onToggle(item.id) }, { Text(item.title, Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.SemiBold) }, Modifier.heightIn(min = 44.dp),
                leadingIcon = if (active) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null, shape = RoundedCornerShape(22.dp), border = null,
                colors = FilterChipDefaults.filterChipColors(containerColor = Soft, labelColor = Quiet, selectedContainerColor = Teal, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White))
        }
    }
}

/** 받은 명함의 "함께한 활동 · ○○ 외 N개" 라벨. 확인 아이콘 없이 긴 활동 이름만 말줄임한다. 첫 활동(일정이 가장 이른 것) 선택은 호출하는 쪽 모델이 정한다. */
@Composable fun DearbyTogetherActivityLabel(title: String, modifier: Modifier = Modifier, otherCount: Int = 0) {
    val suffix = if (otherCount > 0) " 외 ${otherCount}개" else ""
    Row(modifier.clearAndSetSemantics { contentDescription = "함께한 활동, $title$suffix" }) {
        Text("함께한 활동 · ", color = Quiet, maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodyMedium)
        Text(title, Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        if (suffix.isNotEmpty()) Text(suffix, color = Quiet, maxLines = 1, softWrap = false, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun DearbyInlineField(label: String, value: String, onValueChange: (String) -> Unit, editing: Boolean, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val hint = placeholder.ifEmpty { label }
    val box = modifier.fillMaxWidth().background(if (editing) Soft else Color.Transparent, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
    if (!editing) {
        Text(value.ifEmpty { placeholder }, box.semantics { contentDescription = "$label, ${value.ifEmpty { "비어 있음" }}" }, color = if (value.isEmpty()) Quiet else style.color, style = style)
        return
    }
    BasicTextField(value, onValueChange, box.semantics { contentDescription = label }.drawBehind {
        drawLine(Teal, Offset(8.dp.toPx(), size.height), Offset(size.width - 8.dp.toPx(), size.height), 1.dp.toPx())
    }, textStyle = style, singleLine = singleLine, maxLines = if (singleLine) 1 else 6, cursorBrush = SolidColor(Teal)) { inner ->
        Box { if (value.isEmpty()) Text(hint, color = Quiet, style = style); inner() }
    }
}
