package com.dearby.nativeapp.pages.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * First-launch intro as a [CenteredMessage] (contract "비스크롤 화면 배치·CTA"): the sign-in sheet's lockup (a little larger) centred above,
 * no title or explanation (2026-10-25 user decision),
 * then the CTAs stacked at the bottom — Apple, Google ([google] null hides it), then "패스키로 로그인" (existing accounts; new ones start with Apple or
 * Google) and "둘러보기" as text buttons, with [SignInNotice] right under the provider buttons (2026-10-25, same order as iOS). [message] is a failed
 * provider start. Drawn outside `DearbyApp`, so it pads the safe area itself.
 */
@Composable fun IntroPage(start: () -> Unit, apple: () -> Unit, google: (() -> Unit)?, browse: () -> Unit, privacyUrl: String, busy: Boolean = false, message: String? = null) = Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    CenteredMessage(null, null, null, Modifier.fillMaxSize().safeDrawingPadding(),
        symbol = { DearbyLockup(Modifier.height(112.dp)) }, content = { message?.let { DearbyFieldError(it) } },
        actions = {
            AppleSignInButton(apple, Modifier.fillMaxWidth().testTag("intro-apple"), enabled = !busy)
            google?.let { DearbyGoogleButton(it, Modifier.fillMaxWidth().testTag("intro-google"), enabled = !busy) }
            SignInNotice(privacyUrl, Modifier.fillMaxWidth())
            TextButton(start, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("intro-passkey"), enabled = !busy) { Text("패스키로 로그인") }
        },
        secondary = MessageAction("둘러보기", browse, testTag = "intro-browse"))
}
