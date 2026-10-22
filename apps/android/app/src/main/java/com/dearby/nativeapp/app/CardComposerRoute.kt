package com.dearby.nativeapp.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardComposer

/** Makes a real card from the saved profile: choose what to show, publish (the body is the built-in template) — or, opened from `이 명함 고치기`, saves onto that same card (contract "명함 고치기는 같은 명함을 수정"). Signing in is asked for when the composer opens signed out. */
@Composable fun CardComposerRoute(model: CardPublishViewModel, account: AccountViewModel, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    LaunchedEffect(auth.phase) { if (auth.phase == AccountPhase.SIGNED_IN && state.draft == null) model.load() }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar(if (state.prefill == null) "명함 만들기" else "명함 고치기", close)
        val phase = state.phase
        val draft = state.draft
        when {
            // Result, sign-in and "no profile yet" states are CenteredMessages (contract "비스크롤 화면 배치·CTA"): no card, one text scale, CTA at the bottom.
            phase is PublishPhase.Published -> CenteredMessage(Icons.Rounded.CheckCircle, if (state.prefill == null) "명함을 발행했어요" else "고친 내용을 저장했어요", "${phase.card.profileName} · ${phase.card.name}", Modifier.fillMaxSize(),
                primary = MessageAction("확인", close))
            auth.phase != AccountPhase.SIGNED_IN -> CenteredMessage(Icons.Rounded.Badge, "로그인이 필요해요", "로그인하면 프로필 데이터로 명함을 만들 수 있어요.", Modifier.fillMaxSize(),
                primary = MessageAction("로그인", { model.load() }))
            // Signed in from here without ever saving a profile: nothing to fill the template with yet.
            draft != null && draft.profile.version == 0 -> CenteredMessage(Icons.Rounded.AccountCircle, "먼저 프로필을 적어 주세요", "내 프로필에서 이름과 연락처를 적으면 명함을 만들 수 있어요.", Modifier.fillMaxSize(),
                primary = MessageAction("닫기", close))
            draft != null -> CardComposer(draft.cardTitle, { model.edit(draft.copy(cardTitle = it)) }, draft.preview, draft.contacts,
                { id, on -> model.edit(draft.copy(publicContactIds = if (on) draft.publicContactIds + id else draft.publicContactIds - id)) },
                draft.histories, { id, on -> model.edit(draft.copy(publicHistoryIds = if (on) draft.publicHistoryIds + id else draft.publicHistoryIds - id)) },
                draft.changed, { model.edit(draft.revert()) }, model::publish, Modifier.weight(1f),
                publishing = phase == PublishPhase.Publishing || phase == PublishPhase.Loading, errorMessage = (phase as? PublishPhase.Failed)?.message,
                publishLabel = if (state.prefill == null) "명함 발행" else "고친 내용 저장")
            phase is PublishPhase.Failed -> DearbyErrorState("명함을 준비하지 못했어요", phase.message, { model.load() }, Modifier.fillMaxSize())
            // Preparing the draft from the profile: the card preview's outline (contract "내용 로딩은 스켈레톤").
            else -> DearbySkeleton(Modifier.fillMaxSize().padding(20.dp)) { SkeletonLine(.4f, 20.dp); SkeletonCardBody() }
        }
    }
    if (state.signingIn && auth.phase != AccountPhase.SIGNED_IN) SignInRoute(account, close = model::cancelSignIn)
}
