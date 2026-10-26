package com.dearby.nativeapp.pages.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import com.dearby.nativeapp.shared.ui.*

/**
 * A first-seen Apple or Google account (contract "애플 로그인"·"구글 로그인", 202) whose device-passkey prompt was closed. The server
 * cannot tell whether this person already has a passkey account, so they choose: a new account (primary CTA), or passkey sign-in that
 * links the provider account to it (text button). [providerLabel] is Apple or Google; [email] may be empty or Apple's relay. Laid out as a
 * [CenteredMessage] (contract "비스크롤 화면 배치·CTA").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LinkAccountSheet(providerLabel: String, email: String, busy: Boolean, message: String?, startNew: () -> Unit, linkPasskey: () -> Unit, close: () -> Unit) {
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        CenteredMessage(Icons.Rounded.AccountCircle, "처음 보는 $providerLabel 계정이에요", "패스키 확인을 건너뛰었어요.",
            content = {
                if (email.isNotEmpty()) Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                Text("이미 패스키로 만든 계정이 있다면 연결하고, 없다면 새 계정으로 시작해 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                message?.let { DearbyFieldError(it) }
            },
            primary = MessageAction("새 계정으로 시작", startNew, busy = busy, testTag = "link-start-new"),
            secondary = MessageAction("기존 패스키 계정에 연결", linkPasskey, enabled = !busy, testTag = "link-passkey"))
    }
}
