package com.dearby.nativeapp.app

import android.app.Activity
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.features.ads.AdConsentSheet
import com.dearby.nativeapp.features.ads.NativeAdCard
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.pages.queue.DiscoveryQueuePage

/**
 * 오늘의 맞춤 대기열 over the tabs. The system's 애니메이션 삭제 (animator scale 0) is the contract's 동작 줄이기; with TalkBack on the scenes do not
 * advance by themselves. The cover is dark, so the status bar icons turn light while it shows.
 */
@Composable fun QueueRoute(model: QueueViewModel, openDetail: (String) -> Unit, liked: Set<String>, inMine: Set<String>, like: (String, Boolean?) -> Unit, plus: (String) -> Unit, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val askAdConsent by model.adConsentAsk.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduceMotion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val screenReader = remember { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    val view = LocalView.current
    DisposableEffect(view) {
        val bars = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val light = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { light?.let { bars.isAppearanceLightStatusBars = it } }
    }
    val leave = { model.exit(); model.close(); close() }
    BackHandler(onBack = leave)
    DiscoveryQueuePage(state, reduceMotion, !screenReader, leave, model::retry, model::page, like,
        { model.openedDetail(it); model.close(); openDetail(it) }, model::react, model::complete, model::skipEarly, model::refill, model::undo, model::commit, liked, inMine, plus, model::cardViewed,
        ad = model.ads.ad?.let { ad -> { modifier -> NativeAdCard(ad, modifier) } })
    if (askAdConsent != null) AdConsentSheet(model::agreeAd, model::declineAd) { OAuthBrowser(context).open(ConsentVersions.privacyUrl(BuildConfig.WEB_ORIGIN)) }
}
