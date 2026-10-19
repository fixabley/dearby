package com.dearby.nativeapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardComposer

/** Makes a real card from the saved profile: choose what to show, publish (the body is the built-in template). Signing in is asked for when the composer opens signed out. */
@Composable fun CardComposerRoute(model: CardPublishViewModel, account: AccountViewModel, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    LaunchedEffect(auth.phase) { if (auth.phase == AccountPhase.SIGNED_IN && state.draft == null) model.load() }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (state.prefill == null) "명함 만들기" else "명함 고치기", close)
        val phase = state.phase
        val draft = state.draft
        when {
            phase is PublishPhase.Published -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text("명함을 발행했어요", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text("${phase.card.profileName} · ${phase.card.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                DearbyButton(close, Modifier.fillMaxWidth()) { Text("확인") }
            }
            auth.phase != AccountPhase.SIGNED_IN -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Text("로그인하면 프로필 데이터로 명함을 만들 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                DearbyButton({ model.load() }, Modifier.fillMaxWidth()) { Text("로그인") }
            }
            draft != null && draft.profile.version == 0 -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                // Signed in from here without ever saving a profile: nothing to fill the template with yet.
                Text("먼저 내 프로필에서 이름과 연락처를 적어 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                DearbyOutlineButton(close, Modifier.fillMaxWidth()) { Text("닫기") }
            }
            draft != null -> CardComposer(draft.cardTitle, { model.edit(draft.copy(cardTitle = it)) }, draft.preview, draft.contacts,
                { id, on -> model.edit(draft.copy(publicContactIds = if (on) draft.publicContactIds + id else draft.publicContactIds - id)) },
                draft.histories, { id, on -> model.edit(draft.copy(publicHistoryIds = if (on) draft.publicHistoryIds + id else draft.publicHistoryIds - id)) },
                draft.changed, { model.edit(draft.revert()) }, model::publish, Modifier.weight(1f),
                publishing = phase == PublishPhase.Publishing || phase == PublishPhase.Loading, errorMessage = (phase as? PublishPhase.Failed)?.message)
            phase is PublishPhase.Failed -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Text(phase.message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                DearbyOutlineButton({ model.load() }, Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        }
    }
    if (state.signingIn && auth.phase != AccountPhase.SIGNED_IN) SignInRoute(account, close = model::cancelSignIn)
}
