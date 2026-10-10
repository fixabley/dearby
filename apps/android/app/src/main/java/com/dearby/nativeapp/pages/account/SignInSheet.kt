package com.dearby.nativeapp.pages.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * Passkey sign-in shown only when an action needs an account. "패스키로 로그인" comes first; "새 패스키로 시작"
 * makes the passkey and the account together. The OS prompt does the rest, so there is nothing to type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SignInSheet(busy: Boolean, message: String?, signIn: () -> Unit, signUp: () -> Unit, close: () -> Unit) {
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("패스키로 로그인", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text("기기 잠금(지문·얼굴·PIN)으로 로그인해요. 비밀번호는 필요 없어요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
            }
            message?.let { DearbyFieldError(it) }
            DearbyButton(signIn, Modifier.fillMaxWidth().testTag("sign-in-passkey"), enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("패스키로 로그인")
            }
            DearbyOutlineButton(signUp, Modifier.fillMaxWidth().testTag("sign-up-passkey"), enabled = !busy) { Text("새 패스키로 시작") }
            Text("처음이라면 새 패스키를 만들면 계정도 함께 만들어져요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
    }
}
