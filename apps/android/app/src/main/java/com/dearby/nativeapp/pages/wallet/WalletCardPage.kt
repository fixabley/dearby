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

/** One received card opened from the wallet list: the same card face as 내 명함, with no action button (contract "받은 명함 상세"). */
@Composable fun WalletCardPage(card: CardState, close: () -> Unit, contact: (ContactState) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("받은 명함", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            CardContent(card, onContact = contact)
        }
    }
}
