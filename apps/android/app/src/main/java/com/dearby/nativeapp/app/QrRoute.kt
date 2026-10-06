package com.dearby.nativeapp.app

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.pages.qr.QrPage
import com.dearby.nativeapp.shared.ui.DearbyChoice

/** QR tab: shares the account's card on entry; the share button hands the same URL to the system chooser. */
@Composable fun QrRoute(model: QrShareViewModel, catalog: CatalogViewModel, create: () -> Unit, scanned: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val applied by catalog.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.load() }
    QrPage(state, applied.appliedActivities.map { DearbyChoice(it.id, it.title) }, model::select, model::toggle,
        onShare = { url ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            context.startActivity(Intent.createChooser(send, null))
        }, retry = model::load, create = create, scanned = scanned)
}
