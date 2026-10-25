package com.dearby.nativeapp.pages.account

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dearby.nativeapp.shared.ui.*

/**
 * Sign-in shown only when an action needs an account, laid out as a [CenteredMessage] (contract "비스크롤 화면 배치·CTA"): the brand symbol,
 * (the lockup image: wordmark + catchphrase "대외활동, 매일 맞춤으로") one title and one explanation, then the CTAs stacked full width — "Apple로 계속하기" and "Google로 계속하기" first (as on iOS, 2026-10-25), "패스키로 로그인" below as the text button; Apple and Google open the
 * provider ([google] null hides the button: the server has no Google sign-in). New accounts start only with Apple or Google (2026-10-25).
 * The OS prompt or the provider does the rest, so there is nothing to type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SignInSheet(busy: Boolean, message: String?, signIn: () -> Unit, apple: () -> Unit, google: (() -> Unit)?, close: () -> Unit) {
    ModalBottomSheet(close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        // Fixed at half the screen (2026-10-25): skipPartiallyExpanded leaves one expanded state, and the body is half the height,
        // so the sheet does not move between partial and full. On a small screen the heading scrolls and the buttons stay in view.
        CenteredMessage(null, "로그인", "패스키는 기기 잠금(지문·얼굴·PIN)으로 로그인해요. 비밀번호는 필요 없어요.", Modifier.fillMaxHeight(0.5f),
            symbol = { DearbyLockup() },
            content = {
                message?.let { DearbyFieldError(it) }
            },
            actions = {
                AppleSignInButton(apple, Modifier.fillMaxWidth().testTag("sign-in-apple"), enabled = !busy)
                google?.let { DearbyGoogleButton(it, Modifier.fillMaxWidth().testTag("sign-in-google"), enabled = !busy) }
            },
            secondary = MessageAction("패스키로 로그인", signIn, enabled = !busy, testTag = "sign-in-passkey"))
    }
}
