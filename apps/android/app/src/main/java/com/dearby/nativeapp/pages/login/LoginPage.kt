package com.dearby.nativeapp.pages.login

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.dearby.nativeapp.shared.ui.*

@Composable fun LoginPage(busy: Boolean, challenge: String?, expires: String?, request: (String) -> Unit, login: (String) -> Unit, close: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    FormColumn {
        TextButton(close) { Text("닫기") }
        Text("이메일로 로그인", style = MaterialTheme.typography.headlineMedium)
        Field("이메일", email, { email = it })
        Button({ request(email) }, enabled = !busy && email.isNotBlank()) { Text(if (challenge == null) "인증번호 받기" else "인증번호 재전송") }
        if (challenge != null) {
            Text("만료 시각: $expires")
            Field("이메일 인증번호", code, { code = it })
            Button({ login(code) }, enabled = !busy && code.isNotBlank()) { Text("로그인") }
        }
        Text("이메일 발송 또는 인증에 실패하면 로그인되지 않습니다.")
    }
}
