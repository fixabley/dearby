package com.dearby.nativeapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.cards.MyCardsPage

/** 내 명함 tab: reloads on entry (also after the composer closes), so a just-published card shows up. */
@Composable fun MyCardsRoute(model: MyCardsViewModel, create: () -> Unit, edit: (PublishedCard) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.load() }
    MyCardsPage(state, create, edit = { id -> model.card(id)?.let(edit) }, retry = model::load)
}
