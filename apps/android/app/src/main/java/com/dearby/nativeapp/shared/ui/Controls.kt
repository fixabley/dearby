package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
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
            Row(Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceContainer, shape).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, null, Modifier.size(if (progress < .5f) 16.dp else 22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(placeholder, Modifier.alpha((progress * 2 - 1).coerceAtLeast(0f)), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusOnShow) { focus.requestFocus(); focusOnShow = false } }
    BasicTextField(query, onQueryChange, modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = placeholder },
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)) { inner ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(MaterialTheme.colorScheme.surfaceContainer, shape).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Search, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) { if (query.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodyMedium); inner() }
            if (query.isNotEmpty()) IconButton({ onQueryChange("") }) { Icon(Icons.Outlined.Cancel, "검색어 지우기", tint = MaterialTheme.colorScheme.onSurfaceVariant) } else Spacer(Modifier.width(14.dp))
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

/** [focusRequester] lets a funnel with one field per screen open the keyboard as the step appears. */
@Composable fun DearbyInlineField(label: String, value: String, onValueChange: (String) -> Unit, editing: Boolean, modifier: Modifier = Modifier, placeholder: String = "", singleLine: Boolean = true, style: TextStyle = MaterialTheme.typography.bodyLarge, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, focusRequester: FocusRequester? = null) {
    val hint = placeholder.ifEmpty { label }
    val box = modifier.fillMaxWidth().background(if (editing) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
    if (!editing) {
        Text(value.ifEmpty { placeholder }, box.semantics { contentDescription = "$label, ${value.ifEmpty { "비어 있음" }}" }, color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else style.color, style = style)
        return
    }
    val underline = MaterialTheme.colorScheme.primary
    BasicTextField(value, onValueChange, box.semantics { contentDescription = label }.drawBehind {
        drawLine(underline, Offset(8.dp.toPx(), size.height), Offset(size.width - 8.dp.toPx(), size.height), 1.dp.toPx())
    }.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier), textStyle = style, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, singleLine = singleLine, maxLines = if (singleLine) 1 else 6, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)) { inner ->
        Box { if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = style); inner() }
    }
}
