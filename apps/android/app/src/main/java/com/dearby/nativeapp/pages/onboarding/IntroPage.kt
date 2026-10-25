package com.dearby.nativeapp.pages.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * First-launch intro as a [CenteredMessage] (contract "비스크롤 화면 배치·CTA"): symbol + app name, one title and explanation in the middle,
 * then the CTAs stacked at the bottom — Apple, Google ([google] null hides it), then "패스키로 로그인" (existing accounts; new ones start with Apple or
 * Google) and "둘러보기" as text buttons, with [SignInNotice] right under the provider buttons (2026-10-25, same order as iOS). [message] is a failed
 * provider start. Drawn outside `DearbyApp`, so it pads the safe area itself.
 */
@Composable fun IntroPage(start: () -> Unit, apple: () -> Unit, google: (() -> Unit)?, browse: () -> Unit, privacyUrl: String, busy: Boolean = false, message: String? = null) = Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    CenteredMessage(null, "활동에서 만난 사람과\n명함을 주고받아요", "로그인하지 않아도 둘러볼 수 있어요.", Modifier.fillMaxSize().safeDrawingPadding(),
        symbol = { DearbyBrand(symbolSize = 96.dp) }, content = { message?.let { DearbyFieldError(it) } },
        actions = {
            AppleSignInButton(apple, Modifier.fillMaxWidth().testTag("intro-apple"), enabled = !busy)
            google?.let { DearbyGoogleButton(it, Modifier.fillMaxWidth().testTag("intro-google"), enabled = !busy) }
            SignInNotice(privacyUrl, Modifier.fillMaxWidth())
            TextButton(start, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("intro-passkey"), enabled = !busy) { Text("패스키로 로그인") }
        },
        secondary = MessageAction("둘러보기", browse, testTag = "intro-browse"))
}
