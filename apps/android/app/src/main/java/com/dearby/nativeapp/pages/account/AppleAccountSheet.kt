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
 * A first-seen Apple account (contract "애플 로그인", 202). The server cannot tell whether this person already has a passkey
 * account, so they choose: a new account, or passkey sign-in that links Apple to it. [email] may be empty or Apple's relay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppleAccountSheet(email: String, busy: Boolean, message: String?, startNew: () -> Unit, linkPasskey: () -> Unit, close: () -> Unit) {
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("처음 보는 Apple 계정이에요", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                if (email.isNotEmpty()) Text(email, color = Quiet, style = MaterialTheme.typography.bodyMedium)
                Text("이미 패스키로 만든 계정이 있다면 연결하고, 없다면 새 계정으로 시작해 주세요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
            }
            message?.let { DearbyFieldError(it) }
            DearbyButton(startNew, Modifier.fillMaxWidth().testTag("apple-start-new"), enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("새 계정으로 시작")
            }
            DearbyOutlineButton(linkPasskey, Modifier.fillMaxWidth().testTag("apple-link-passkey"), enabled = !busy) { Text("기존 패스키 계정에 연결") }
        }
    }
}
