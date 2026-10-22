package com.dearby.nativeapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.cards.MyCardsPage

/** 내 명함 tab: reloads on entry (also after the composer closes), so a just-published card shows up, and prefetches the selected card's QR code. */
@Composable fun MyCardsRoute(model: MyCardsViewModel, create: () -> Unit, showQr: (PublishedCard) -> Unit, edit: (PublishedCard) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.load() }
    // Contract "명함 QR 코드" 미리 받기: the selected card gets a code while this section is visible.
    LaunchedEffect(state.selectedId) { state.selectedId?.let(model::prefetch) }
    MyCardsPage(state, create, select = model::select, showQr = { id -> model.card(id)?.let(showQr) }, edit = { id -> model.card(id)?.let(edit) }, delete = model::delete, contact = { openContact(context, it) }, retry = model::load)
}
