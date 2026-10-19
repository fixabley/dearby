package com.dearby.nativeapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.cards.MyCardsPage

/** 내 명함 tab: reloads on entry (also after the composer closes), so a just-published card shows up. */
@Composable fun MyCardsRoute(model: MyCardsViewModel, create: () -> Unit, showQr: (PublishedCard) -> Unit, settings: () -> Unit, notify: () -> Unit, edit: (PublishedCard) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.load() }
    MyCardsPage(state, create, select = model::select, showQr = { id -> model.card(id)?.let(showQr) }, edit = { id -> model.card(id)?.let(edit) }, delete = model::delete, contact = { openContact(context, it) }, retry = model::load, settings = settings, notify = notify)
}
