package com.dearby.nativeapp.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.wallet.ReceivedCard
import com.dearby.nativeapp.shared.ui.DearbyTopBar
import com.dearby.nativeapp.shared.ui.TabActions

/** The two sections of the 명함 tab (contract "명함 탭 통합", 2026-10-20). */
enum class CardsSegment(val label: String) { MINE("내 명함"), RECEIVED("받은 명함") }

/**
 * 명함 tab: one pinned top bar (설정·알림), a Material 3 `PrimaryTabRow` switching between the existing 내 명함 and 받은 명함 contents, which keep their
 * own routes and view models. Scrolling inside either section tints the bar through the nested-scroll connection on this column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CardsRoute(
    segment: CardsSegment, select: (CardsSegment) -> Unit, myCards: MyCardsViewModel, wallet: WalletViewModel, account: AccountViewModel,
    create: () -> Unit, showQr: (PublishedCard) -> Unit, edit: (PublishedCard) -> Unit, openReceived: (ReceivedCard) -> Unit, settings: () -> Unit, notify: () -> Unit,
) {
    val topBar = TopAppBarDefaults.pinnedScrollBehavior()
    Column(Modifier.fillMaxSize().nestedScroll(topBar.nestedScrollConnection)) {
        DearbyTopBar("명함", scrollBehavior = topBar) { TabActions(settings, notify) }
        PrimaryTabRow(segment.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            CardsSegment.entries.forEach { Tab(segment == it, { select(it) }, text = { Text(it.label) }) }
        }
        when (segment) {
            CardsSegment.MINE -> MyCardsRoute(myCards, create, showQr, edit)
            CardsSegment.RECEIVED -> WalletRoute(wallet, account, openReceived)
        }
    }
}
