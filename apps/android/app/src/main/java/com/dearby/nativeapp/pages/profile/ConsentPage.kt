package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.shared.ui.DearbyFieldError
import com.dearby.nativeapp.shared.ui.Quiet
import com.dearby.nativeapp.shared.ui.ScreenHeader

/**
 * 개인정보 동의 (contract "개인정보 동의", 2026-10-19): shown full screen before the first profile step and whenever the server says
 * consent is required. Both boxes are required; `보기` opens the web document (the route layer launches the browser).
 */
@Composable fun ConsentPage(consenting: Boolean, error: String?, openPrivacy: () -> Unit, openTerms: () -> Unit, agree: () -> Unit) {
    var privacy by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("시작하기 전에 동의가 필요해요")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("프로필(이름·연락처·활동 이력)을 저장하려면 아래 두 가지에 동의해 주세요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
            ConsentRow("개인정보 수집·이용에 동의합니다 (필수)", privacy, { privacy = it }, openPrivacy)
            ConsentRow("이용약관에 동의합니다 (필수)", terms, { terms = it }, openTerms)
            error?.let { DearbyFieldError(it) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            DearbyButton(agree, Modifier.fillMaxWidth(), enabled = privacy && terms && !consenting) { Text(if (consenting) "저장하는 중…" else "동의하고 계속") }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable private fun ConsentRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, open: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked)
        Text(label, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.bodyLarge)
        TextButton(open) { Text("보기") }
    }
}
