package com.dearby.nativeapp.features.ads

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.R
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.concurrent.thread

/**
 * 맞춤 대기열 끝 광고 (contract "맞춤 대기열 끝 광고(AdMob, 비개인화)", 2026-10-25): one AdMob native ad, requested once the queue has loaded
 * and shown after its last activity. Non-personalised (`npa=1`), PG content ceiling, publisher first-party id off; no UMP form (Korea only).
 * [load] is called only after 광고 목적 개인정보 제3자 제공 동의 (QueueViewModel), and the SDK is initialised on that first [load], never at launch.
 * Without a native unit id (a release built without the AdMob ids), or with ads off (`BuildConfig.ADS_ENABLED`, 2026-10-26 user decision: off
 * unless the build sets `DEARBY_ADS_ENABLED=true`), nothing is initialised or requested and [ad] stays null. [load] replaces the
 * held ad and calls [loaded] when the new one arrives; [requested] says a request went out for this view and [failed] tells a failed request
 * from one still loading. [clear] destroys the held ad.
 */
class QueueAd(private val context: Context) {
    var ad by mutableStateOf<NativeAd?>(null)
        private set
    var failed = false
        private set
    var requested = false
        private set
    private var serial = 0

    fun load(loaded: () -> Unit) {
        clear()
        if (!enabled) return
        requested = true
        val mine = serial
        whenReady(context) { if (mine == serial) request(mine, loaded) }
    }

    private fun request(mine: Int, loaded: () -> Unit) {
        AdLoader.Builder(context, BuildConfig.ADMOB_NATIVE_UNIT_ID)
            .forNativeAd { new -> if (mine == serial) { ad = new; loaded() } else new.destroy() }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("Dearby", "Queue ad failed to load: ${error.code}")
                    if (mine == serial) failed = true
                }
            })
            // ANY, not portrait only: Google warns that narrowing the ratio leaves fewer ads to fill. AdChoices goes to the corner away from
            // the close button (top right, above the ad) and the call to action (bottom).
            .withNativeAdOptions(NativeAdOptions.Builder().setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_LEFT).build())
            .build()
            .loadAd(AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") }).build())
    }

    /** Starts the SDK without requesting an ad (consent given after this view's ad place had passed). */
    fun start() { if (enabled) whenReady(context) {} }

    /** A late answer to an earlier [load] is destroyed on arrival. */
    fun clear() {
        serial++
        failed = false
        requested = false
        ad?.destroy()
        ad = null
    }

    companion object {
        /** Ads on for this build: the switch and a unit id. Off, the queue neither asks for consent nor requests an ad, and 설정 hides its switch. */
        val enabled get() = BuildConfig.ADS_ENABLED && BuildConfig.ADMOB_NATIVE_UNIT_ID.isNotEmpty()
        private var started = false
        private var ready = false
        private var waiting: (() -> Unit)? = null

        /**
         * Initialises the SDK once, off the main thread, and runs [then] on the main thread when it is ready. The first-party id switch throws
         * before initialisation, so no request goes out until it is off.
         */
        private fun whenReady(context: Context, then: () -> Unit) {
            if (ready) return then()
            waiting = then
            if (started) return
            started = true
            MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build())
            thread(name = "MobileAdsInit") {
                MobileAds.initialize(context) {
                    Handler(Looper.getMainLooper()).post {
                        MobileAds.putPublisherFirstPartyIdEnabled(false)
                        ready = true
                        waiting?.invoke(); waiting = null
                    }
                }
            }
        }
    }
}

/**
 * The ad as a full-screen story card: `광고` badge and advertiser, headline, media, body and the call to action, every asset registered on the
 * [NativeAdView] unchanged (the SDK draws AdChoices in the top-left corner). Missing optional assets are hidden.
 */
@Composable fun NativeAdCard(ad: NativeAd, modifier: Modifier = Modifier) {
    AndroidView({ LayoutInflater.from(it).inflate(R.layout.queue_native_ad, FrameLayout(it), false) as NativeAdView }, modifier, update = { view ->
        val advertiser = view.findViewById<TextView>(R.id.ad_advertiser)
        val headline = view.findViewById<TextView>(R.id.ad_headline)
        val body = view.findViewById<TextView>(R.id.ad_body)
        val action = view.findViewById<Button>(R.id.ad_call_to_action)
        advertiser.show(ad.advertiser); headline.show(ad.headline); body.show(ad.body); action.show(ad.callToAction)
        view.advertiserView = advertiser
        view.headlineView = headline
        view.bodyView = body
        view.callToActionView = action
        view.mediaView = view.findViewById<MediaView>(R.id.ad_media)
        view.setNativeAd(ad)
    })
}

private fun TextView.show(value: String?) {
    text = value
    visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
}
