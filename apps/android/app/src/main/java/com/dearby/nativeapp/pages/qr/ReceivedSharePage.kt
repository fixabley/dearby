package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

enum class ReceivedPhase { LOADING, LOADED, MISSING, FAILED }
/** A received card from a scanned QR or `/s/<id>` link, with the activities its sender chose. */
data class ReceivedShareState(val phase: ReceivedPhase = ReceivedPhase.LOADING, val card: CardState? = null, val activities: List<String> = emptyList())
/** Saving to the account's wallet: shown for shares only. [done] is the saved message, [error] the failure text. */
data class ReceivedSaveState(val signedIn: Boolean, val saving: Boolean = false, val done: String? = null, val error: String? = null)

@Composable fun ReceivedSharePage(state: ReceivedShareState, close: () -> Unit, retry: () -> Unit, contact: (ContactState) -> Unit,
                                  save: ReceivedSaveState? = null, onSave: () -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("공유 명함", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state.phase) {
                ReceivedPhase.LOADING -> Box(Modifier.fillMaxWidth().heightIn(min = 240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
                ReceivedPhase.MISSING -> Notice("명함을 찾을 수 없어요", "공유한 사람이 명함을 거둬들였거나 잘못된 QR이에요.")
                ReceivedPhase.FAILED -> {
                    Notice("명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.")
                    DearbyOutlineButton(retry, Modifier.fillMaxWidth()) { Text("다시 시도") }
                }
                ReceivedPhase.LOADED -> {
                    state.card?.let { CardContent(it, onContact = contact, expanded = true) }
                    if (state.activities.isNotEmpty()) {
                        HorizontalDivider()
                        Text("함께 공유된 활동", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        state.activities.forEach { title ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Event, null, tint = Teal); Text(title, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Text("공유한 사람이 고른 활동이에요. 참가 확인은 아니에요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (save != null && state.phase == ReceivedPhase.LOADED) SaveBar(save, onSave)
    }
}

@Composable private fun SaveBar(save: ReceivedSaveState, onSave: () -> Unit) {
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (save.done != null) {
            Text(save.done, Modifier.fillMaxWidth(), color = Teal, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
        } else {
            save.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            DearbyButton(onSave, Modifier.fillMaxWidth(), enabled = !save.saving) {
                if (save.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("받은 명함에 저장")
            }
            if (!save.signedIn) Text("저장할 때만 이메일 인증번호로 로그인해요.", Modifier.fillMaxWidth(), color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Notice(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}
