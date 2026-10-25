package com.dearby.nativeapp.pages.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * Sign-in shown only when an action needs an account, laid out as a [CenteredMessage] (contract "비스크롤 화면 배치·CTA"): the brand symbol,
 * (the horizontal wordmark with the catchphrase "대외활동, 매일 맞춤으로") one title and one explanation, then the CTAs stacked full width — "패스키로 로그인" first; "Apple로 계속하기" and "Google로 계속하기" open the
 * provider ([google] null hides the button: the server has no Google sign-in). New accounts start only with Apple or Google (2026-10-25).
 * The OS prompt or the provider does the rest, so there is nothing to type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SignInSheet(busy: Boolean, message: String?, signIn: () -> Unit, apple: () -> Unit, google: (() -> Unit)?, close: () -> Unit) {
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        CenteredMessage(null, "로그인", "패스키는 기기 잠금(지문·얼굴·PIN)으로 로그인해요. 비밀번호는 필요 없어요.",
            symbol = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DearbyWordmark()
                    Text("대외활동, 매일 맞춤으로", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            },
            content = {
                Text("처음이라면 Apple이나 Google 계정으로 시작해요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                message?.let { DearbyFieldError(it) }
            },
            primary = MessageAction("패스키로 로그인", signIn, busy = busy, testTag = "sign-in-passkey"),
            actions = {
                AppleSignInButton(apple, Modifier.fillMaxWidth().testTag("sign-in-apple"), enabled = !busy)
                google?.let { DearbyGoogleButton(it, Modifier.fillMaxWidth().testTag("sign-in-google"), enabled = !busy) }
            })
    }
}
