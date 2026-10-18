package com.dearby.nativeapp.app

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * 내 명함 QR 보이기 (contract "명함 교환"): the QR encodes the public card page plus the activity chip. The chip starts on the
 * 진행 중 activity whose next stage is nearest (the progress track's order), and the screen brightness is raised while shown.
 */
@Composable fun CardQrRoute(card: PublishedCard, activityModel: ActivityViewModel, close: () -> Unit) {
    val activities by activityModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(activities.activeActivities.firstOrNull()?.id) }
    val applied = activities.appliedActivities.map { QrActivity(it.id, it.title) }
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val before = window.attributes.screenBrightness
        window.attributes = window.attributes.also { it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { window.attributes = window.attributes.also { it.screenBrightness = before } }
    }
    CardQrPage(card.name, cardQrUrl(BuildConfig.WEB_ORIGIN, card.id, selected?.takeIf { id -> applied.any { it.id == id } }), applied, selected, { selected = it }, close)
}
