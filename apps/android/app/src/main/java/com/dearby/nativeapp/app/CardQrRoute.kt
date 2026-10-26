package com.dearby.nativeapp.app

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.entities.account.model.PublishedCard
import com.dearby.nativeapp.pages.cards.CardQrPage
import com.dearby.nativeapp.pages.cards.QrActivity
import com.dearby.nativeapp.shared.config.cardQrUrl

/**
 * 내 명함 QR 보이기 (contracts "명함 교환", "명함 QR 코드"): the QR encodes the card page, the activity chip and the current QR code. The chip starts on the
 * 진행 중 activity whose next stage is nearest (the progress track's order) unless [activityId] names one, and the screen brightness is raised while shown.
 */
@Composable fun CardQrRoute(card: PublishedCard, activityModel: ActivityViewModel, myCards: MyCardsViewModel,
    /** The activity the chip starts on (오늘 내 행사 `명함 QR`, 2026-10-26); null picks the nearest 진행 중 activity. */
    activityId: String? = null, close: () -> Unit) {
    val activities by activityModel.state.collectAsStateWithLifecycle()
    val qr by myCards.qr.collectAsStateWithLifecycle()
    // Contract "명함 QR 코드": a code on open, revoked on leaving (top-bar back, system back or tab change all dispose this route).
    LaunchedEffect(card.id) { myCards.openQr(card.id) }
    DisposableEffect(card.id) { onDispose { myCards.closeQr() } }
    var selected by remember { mutableStateOf(activityId ?: activities.activeActivities.firstOrNull()?.id) }
    val applied = activities.appliedActivities.map { QrActivity(it.id, it.title) }
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val before = window.attributes.screenBrightness
        window.attributes = window.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { window.attributes = window.attributes.also { it.screenBrightness = before } }
    }
    val url = qr.code?.let { cardQrUrl(BuildConfig.WEB_ORIGIN, card.id, selected?.takeIf { id -> applied.any { it.id == id } }, it) }
    CardQrPage(card.name, url, qr.failed, { myCards.refreshQr(card.id) }, applied, selected, { selected = it }, close)
}
