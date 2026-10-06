package com.dearby.nativeapp.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.pages.account.SignInSheet
import com.dearby.nativeapp.pages.qr.ReceivedPhase
import com.dearby.nativeapp.pages.qr.ReceivedSaveState
import com.dearby.nativeapp.pages.qr.ReceivedSharePage
import com.dearby.nativeapp.pages.qr.ReceivedShareState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import kotlinx.coroutines.launch

/**
 * Opens a scanned or linked card without sign-in: `GET /v1/shares/:id`, or `GET /v1/cards/:id` for legacy codes.
 * Shares can be saved to the account's wallet (`PUT /v1/wallet/shares/:id`); sign-in is asked for only then.
 */
@Composable fun ReceivedShareRoute(link: ScannedLink, account: AccountViewModel, close: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState(ReceivedShareState(), link, attempt) {
        value = ReceivedShareState()
        value = try {
            when (link) {
                is ScannedLink.Share -> account.client.publicShare(link.id).let { ReceivedShareState(ReceivedPhase.LOADED, it.card.toState(), it.share.activities.map { a -> a.title }) }
                is ScannedLink.Card -> ReceivedShareState(ReceivedPhase.LOADED, account.client.publicCard(link.id).toState())
            }
        } catch (e: AccountException) {
            ReceivedShareState(if (e.error == AccountError.NOT_FOUND) ReceivedPhase.MISSING else ReceivedPhase.FAILED)
        }
    }
    val auth by account.state.collectAsStateWithLifecycle()
    val signedIn = auth.phase == AccountPhase.SIGNED_IN
    var saving by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save() = scope.launch {
        val share = link as? ScannedLink.Share ?: return@launch
        saving = true; error = null
        try {
            val status = account.authorized { account.client.saveShare(share.id, it) }
            done = if (status == "alreadySaved") "이미 받은 명함에 있어요" else "받은 명함에 저장했어요"
        } catch (e: AccountException) {
            error = when (e.error) {
                AccountError.UNAUTHORIZED -> null.also { signingIn = true }
                AccountError.OWN_CARD -> "내 명함은 받은 명함에 저장하지 않아요."
                AccountError.CONFLICT -> "이 명함으로 받은 공유가 너무 많아 더 저장할 수 없어요."
                AccountError.NOT_FOUND -> "공유한 사람이 명함을 거둬들였어요."
                else -> "저장하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
        } finally { saving = false }
    }
    // Signing in from here continues the save.
    LaunchedEffect(signedIn, signingIn) { if (signingIn && signedIn) { signingIn = false; save() } }
    val context = LocalContext.current
    BackHandler(onBack = close)
    ReceivedSharePage(state, close, { attempt++ }, { openContact(context, it) }, save = (link as? ScannedLink.Share)?.let { ReceivedSaveState(signedIn, saving, done, error) },
        onSave = { if (signedIn) save() else signingIn = true })
    if (signingIn && !signedIn) SignInSheet(
        codeStep = auth.phase == AccountPhase.CODE_SENT || auth.phase == AccountPhase.VERIFYING,
        busy = auth.phase == AccountPhase.SENDING_CODE || auth.phase == AccountPhase.VERIFYING,
        email = auth.email, message = auth.message, codeSentAt = auth.codeSentAt,
        requestCode = { scope.launch { account.requestCode(it) } }, verify = { scope.launch { account.verify(it) } },
        changeEmail = account::changeEmail, close = { signingIn = false },
    )
}

fun PublishedCard.toState() = CardState(id, profileName, job, name, description, introduction,
    contacts.map { ContactState(it.id, it.kind, it.label, it.value) },
    histories.map { CardHistoryState(it.id, it.title, it.role, listOfNotNull(it.startDate, it.endDate).joinToString(" – ")) })
