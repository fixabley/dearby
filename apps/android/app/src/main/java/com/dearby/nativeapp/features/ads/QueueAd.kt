package com.dearby.nativeapp.features.ads

import android.content.Context
import android.os.Bundle
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
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.concurrent.thread

/**
 * 맞춤 대기열 끝 광고 (2026-10-25 user decision): one AdMob native ad, requested when the queue opens and shown after its last activity.
 * Non-personalised (`npa=1`) with the PG content ceiling; no consent form (Korea only). Without a native unit id (a release built without the
 * AdMob ids) nothing is initialised or requested and [ad] stays null. [load] replaces, [clear] destroys the held ad.
 */
class QueueAd(private val context: Context) {
    var ad by mutableStateOf<NativeAd?>(null)
        private set
    private var serial = 0

    fun load() {
        clear()
        if (!enabled) return
        start(context)
        val mine = serial
        AdLoader.Builder(context, BuildConfig.ADMOB_NATIVE_UNIT_ID)
            .forNativeAd { loaded -> if (mine == serial) ad = loaded else loaded.destroy() }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) { Log.w("Dearby", "Queue ad failed to load: ${error.code}") }
            })
            .build()
            .loadAd(AdRequest.Builder().addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") }).build())
    }

    /** A late answer to an earlier [load] is destroyed on arrival. */
    fun clear() {
        serial++
        ad?.destroy()
        ad = null
    }

    companion object {
        val enabled get() = BuildConfig.ADMOB_NATIVE_UNIT_ID.isNotEmpty()
        private var started = false

        private fun start(context: Context) {
            if (started) return
            started = true
            MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build())
            thread(name = "MobileAdsInit") { MobileAds.initialize(context) {} }
        }
    }
}

/**
 * The ad as a full-screen story card: `광고` badge and advertiser, headline, media, body and the call to action, every asset registered on the
 * [NativeAdView] unchanged (the SDK draws AdChoices in its default top-right corner; video starts muted by default). Missing optional assets are hidden.
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
