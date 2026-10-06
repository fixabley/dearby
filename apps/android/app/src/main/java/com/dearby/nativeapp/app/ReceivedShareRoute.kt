package com.dearby.nativeapp.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.pages.qr.ReceivedPhase
import com.dearby.nativeapp.pages.qr.ReceivedSharePage
import com.dearby.nativeapp.pages.qr.ReceivedShareState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

/** Opens a scanned or linked card without sign-in: `GET /v1/shares/:id`, or `GET /v1/cards/:id` for legacy codes. */
@Composable fun ReceivedShareRoute(link: ScannedLink, client: AccountClient, close: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState(ReceivedShareState(), link, attempt) {
        value = ReceivedShareState()
        value = try {
            when (link) {
                is ScannedLink.Share -> client.publicShare(link.id).let { ReceivedShareState(ReceivedPhase.LOADED, it.card.toState(), it.share.activities.map { a -> a.title }) }
                is ScannedLink.Card -> ReceivedShareState(ReceivedPhase.LOADED, client.publicCard(link.id).toState())
            }
        } catch (e: AccountException) {
            ReceivedShareState(if (e.error == AccountError.NOT_FOUND) ReceivedPhase.MISSING else ReceivedPhase.FAILED)
        }
    }
    val context = LocalContext.current
    BackHandler(onBack = close)
    ReceivedSharePage(state, close, { attempt++ }) { contact ->
        // Phone and email open the phone or mail app; links open only when they are https.
        val target = when (contact.kind) {
            "phone" -> "tel:" + contact.value.filter { it.isDigit() || it == '+' }
            "email" -> "mailto:" + contact.value
            else -> contact.value.takeIf { it.startsWith("https://") }
        }
        target?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) } }
    }
}

private fun PublishedCard.toState() = CardState(id, profileName, job, name, description, introduction,
    contacts.map { ContactState(it.id, it.kind, it.label, it.value) },
    histories.map { CardHistoryState(it.id, it.title, it.role, listOfNotNull(it.startDate, it.endDate).joinToString(" – ")) })
