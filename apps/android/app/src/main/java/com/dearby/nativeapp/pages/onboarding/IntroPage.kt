package com.dearby.nativeapp.pages.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** First-launch intro: sign in with a passkey or look around without an account. */
@Composable fun IntroPage(start: () -> Unit, browse: () -> Unit) = Surface(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            DearbyLogo(Modifier.width(184.dp).height(68.dp))
            Spacer(Modifier.height(24.dp))
            Text("활동에서 만난 사람과\n명함을 주고받아요", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("로그인하지 않아도 둘러볼 수 있어요.", color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
        DearbyButton(start, Modifier.fillMaxWidth().testTag("intro-passkey")) { Text("패스키로 시작하기") }
        Spacer(Modifier.height(12.dp))
        DearbyOutlineButton(browse, Modifier.fillMaxWidth().testTag("intro-browse")) { Text("둘러보기") }
    }
}
