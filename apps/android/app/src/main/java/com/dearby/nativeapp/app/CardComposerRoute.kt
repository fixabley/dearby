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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.pages.account.SignInSheet
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardComposer
import kotlinx.coroutines.launch

/** Makes a real card from the QR tab; sign-in is asked for only on publish and publishing continues after. */
@Composable fun CardComposerRoute(model: CardPublishViewModel, account: AccountViewModel, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(auth.phase, state.signingIn) {
        if (state.signingIn && auth.phase == AccountPhase.SIGNED_IN) model.continueAfterSignIn()
    }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("명함 만들기", close)
        val phase = state.phase
        if (phase is PublishPhase.Published) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(56.dp), tint = Teal)
                Text("명함을 발행했어요", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text("${phase.card.profileName} · ${phase.card.name}", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                DearbyButton(close, Modifier.fillMaxWidth()) { Text("확인") }
            }
        } else {
            val draft = state.draft
            CardComposer(draft.name, draft.job, draft.introduction, draft.contacts, draft.histories,
                onNameChange = { model.edit(draft.copy(name = it)) }, onJobChange = { model.edit(draft.copy(job = it)) },
                onIntroductionChange = { model.edit(draft.copy(introduction = it)) },
                onContactChange = { row -> model.edit(draft.copy(contacts = draft.contacts.map { if (it.id == row.id) row else it })) },
                onHistoryChange = { row -> model.edit(draft.copy(histories = draft.histories.map { if (it.id == row.id) row else it })) },
                onPublish = model::publish, modifier = Modifier.weight(1f),
                requiresLogin = auth.phase != AccountPhase.SIGNED_IN,
                publishing = phase == PublishPhase.Publishing || phase == PublishPhase.Loading,
                cardTitle = draft.cardTitle, onCardTitleChange = { model.edit(draft.copy(cardTitle = it)) },
                errorMessage = (phase as? PublishPhase.Failed)?.message ?: auth.message.takeIf { auth.phase == AccountPhase.SIGNED_OUT })
        }
    }
    if (state.signingIn && auth.phase != AccountPhase.SIGNED_IN) SignInSheet(auth.phase == AccountPhase.WORKING, auth.message,
        signIn = { scope.launch { account.signIn(Passkeys(context)) } }, signUp = { scope.launch { account.signUp(Passkeys(context)) } },
        close = model::cancelSignIn,
    )
}
