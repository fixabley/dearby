package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.text.KeyboardOptions
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

/** 묶음 머리글. [expanded]를 주면 누를 때 [onToggle]을 부르는 접고 펴는 버튼이 되고, 펼침 상태를 화살표와 접근성 상태로 알린다. */
@Composable fun DearbySectionHeader(title: String, count: Int, modifier: Modifier = Modifier, expanded: Boolean? = null, onToggle: () -> Unit = {}) {
    val reduceMotion = rememberReduceMotion()
    val rotation by animateFloatAsState(if (expanded == false) -90f else 0f, if (reduceMotion) snap() else spring(), label = "chevron")
    val toggle = if (expanded == null) Modifier else Modifier.clickable(onClickLabel = if (expanded) "접기" else "펼치기", role = Role.Button, onClick = onToggle)
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).then(toggle).padding(top = 8.dp).semantics(mergeDescendants = true) {
        heading(); contentDescription = "$title, ${count}개"
        if (expanded != null) stateDescription = if (expanded) "펼침" else "접힘"
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
            Text("$count", Modifier.background(Mint, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp), color = Teal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        }
        if (expanded != null) Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.rotate(rotation), tint = Quiet)
    }
}

/** 시스템 애니메이션 배율이 0이면 동작 줄이기로 본다. */
@Composable fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * 검색 칸. [expansion] 0은 돋보기만 보이는 얇은 막대, 1은 입력 칸이다. 검색어가 있거나 입력 중이면 항상 펼친다.
 * 동작 줄이기에서는 중간 크기 없이 바로 바뀐다. 줄어든 막대는 접근성 도구에서 `검색` 버튼으로 읽힌다.
 */
@Composable fun DearbySearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, expansion: Float = 1f, onExpand: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    var focusOnShow by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val value = expansion.coerceIn(0f, 1f)
    val progress = if (query.isNotEmpty() || focused || focusOnShow) 1f else if (reduceMotion) (if (value >= .5f) 1f else 0f) else value
    val height = (28 + 20 * progress).dp
    val shape = RoundedCornerShape(50)
    if (progress < 1f) {
        Box(modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { focusOnShow = true; onExpand() }
            .clearAndSetSemantics { contentDescription = "검색"; role = Role.Button }, contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().height(height).background(Soft, shape).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, null, Modifier.size(if (progress < .5f) 16.dp else 22.dp), tint = Quiet)
                Text(placeholder, Modifier.alpha((progress * 2 - 1).coerceAtLeast(0f)), color = Quiet, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusOnShow) { focus.requestFocus(); focusOnShow = false } }
    BasicTextField(query, onQueryChange, modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = placeholder },
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, cursorBrush = SolidColor(Teal)) { inner ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Soft, shape).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Search, null, Modifier.size(22.dp), tint = Quiet)
            Box(Modifier.weight(1f)) { if (query.isEmpty()) Text(placeholder, color = Quiet, maxLines = 1, style = MaterialTheme.typography.bodyMedium); inner() }
            if (query.isNotEmpty()) IconButton({ onQueryChange("") }) { Icon(Icons.Outlined.Cancel, "검색어 지우기", tint = Quiet) } else Spacer(Modifier.width(14.dp))
        }
    }
}

/** 목록 맨 위에서 아래로 당기면 검색 칸을 펼치고, 목록을 위로 밀면 줄인다. 손을 떼면 0.5를 기준으로 0 또는 1로 맞춘다. */
@Stable class DearbySearchReveal internal constructor(private val distancePx: Float, private val reduceMotion: Boolean, private val scope: CoroutineScope) {
    private val value = Animatable(0f)
    val expansion: Float get() = value.value
    fun expand() = settle(1f)
    private fun settle(target: Float) { scope.launch { if (reduceMotion) value.snapTo(target) else value.animateTo(target) } }
    private fun move(delta: Float) { scope.launch { value.snapTo((value.value + delta / distancePx).coerceIn(0f, 1f)) } }
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0 && value.value > 0f) move(available.y)
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0 || source != NestedScrollSource.UserInput) return Offset.Zero
            move(available.y)
            return Offset(0f, available.y)
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            val current = value.value
            if (current > 0f && current < 1f) settle(if (current >= .5f) 1f else 0f)
            return Velocity.Zero
        }
    }
}

@Composable fun rememberDearbySearchReveal(): DearbySearchReveal {
    val distance = with(LocalDensity.current) { 56.dp.toPx() }
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    return remember(distance, reduceMotion) { DearbySearchReveal(distance, reduceMotion, scope) }
}

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

@Composable fun DearbyInlineField(label: String, value: String, onValueChange: (String) -> Unit, editing: Boolean, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, style: TextStyle = MaterialTheme.typography.bodyLarge, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    val hint = placeholder.ifEmpty { label }
    val box = modifier.fillMaxWidth().background(if (editing) Soft else Color.Transparent, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
    if (!editing) {
        Text(value.ifEmpty { placeholder }, box.semantics { contentDescription = "$label, ${value.ifEmpty { "비어 있음" }}" }, color = if (value.isEmpty()) Quiet else style.color, style = style)
        return
    }
    BasicTextField(value, onValueChange, box.semantics { contentDescription = label }.drawBehind {
        drawLine(Teal, Offset(8.dp.toPx(), size.height), Offset(size.width - 8.dp.toPx(), size.height), 1.dp.toPx())
    }, textStyle = style, keyboardOptions = keyboardOptions, singleLine = singleLine, maxLines = if (singleLine) 1 else 6, cursorBrush = SolidColor(Teal)) { inner ->
        Box { if (value.isEmpty()) Text(hint, color = Quiet, style = style); inner() }
    }
}
