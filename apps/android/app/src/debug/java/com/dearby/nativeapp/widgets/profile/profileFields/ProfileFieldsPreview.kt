package com.dearby.nativeapp.widgets.profile.profileFields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyTheme
import java.time.LocalDate

/** 미리보기와 계측 캡처가 함께 쓰는 예시. 연락처는 실제가 아닌 example.invalid 값이다. */
@Composable fun ProfileFieldsSample() {
    var phone by remember { mutableStateOf("010-12") }
    var email by remember { mutableStateOf("jimin@example.invalid") }
    var extras by remember { mutableStateOf(listOf(ProfileExtraContact("x1", "instagram", "@jimin"))) }
    var start by remember { mutableStateOf<LocalDate?>(LocalDate.of(2026, 3, 1)) }
    var end by remember { mutableStateOf<LocalDate?>(LocalDate.of(2026, 6, 1)) }
    var ongoing by remember { mutableStateOf(false) }
    Column(Modifier.background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ProfileContactFields(phone, email, extras, { phone = it }, { email = it }, { c -> extras = extras.map { if (it.id == c.id) c else it } },
            { id -> extras = extras.filterNot { it.id == id } }, { kind -> extras = extras + ProfileExtraContact("n${extras.size}", kind, "") },
            phoneError = "전화번호는 숫자 8~15자리로 입력해 주세요.")
        HistoryPeriodFields(start, end, ongoing, { start = it }, { end = it }, { ongoing = it })
    }
}

@Preview(name = "프로필 입력", widthDp = 390) @Composable private fun ProfileFieldsPreview() = DearbyTheme { ProfileFieldsSample() }
