package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.ScreenHeader
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

/** One received card opened from the wallet list. */
@Composable fun WalletCardPage(card: CardState, close: () -> Unit, contact: (ContactState) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("받은 명함", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            // CardContent scrolls inside itself; inside this scrolling page it needs a bounded height.
            CardContent(card, Modifier.heightIn(max = 560.dp), onContact = contact, expanded = true)
        }
    }
}
