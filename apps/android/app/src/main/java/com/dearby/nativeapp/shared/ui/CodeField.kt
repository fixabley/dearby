package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 오류 글자·테두리. 웹 `--danger`와 같은 값. */
val Danger = Color(0xFFA12A2A)

/** 인증번호 입력 칸. 숫자 칸을 나눠 보이지만 입력은 하나의 BasicTextField가 받아 붙여넣기·자동 입력·숫자 키패드가 그대로 동작한다. */
@Composable fun DearbyCodeField(code: String, onCodeChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, length: Int = 6, isError: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(code, { value -> onCodeChange(value.filter(Char::isDigit).take(length)) }, modifier.fillMaxWidth().semantics { contentDescription = label },
        singleLine = true, interactionSource = interaction, cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)) { _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(length) { index ->
                val current = focused && index == minOf(code.length, length - 1)
                Box(Modifier.weight(1f).heightIn(min = 56.dp).background(Soft, RoundedCornerShape(10.dp))
                    .border(if (current || isError) 2.dp else 1.dp, if (isError) Danger else if (current) Teal else Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(code.getOrNull(index)?.toString() ?: "", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

/** 입력 칸 바로 아래에 붙는 오류 문구. 색만이 아니라 아이콘으로도 오류임을 보인다. */
@Composable fun DearbyFieldError(text: String, modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = Danger)
    Text(text, color = Danger, style = MaterialTheme.typography.bodyMedium)
}
