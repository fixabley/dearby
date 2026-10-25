package com.dearby.nativeapp.features.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * 사용 통계 (2026-10-25 user decision "Analytics SDK 넣어줘", event dictionary `shared/contracts/analytics-v1.md`): Firebase Analytics with the privacy-minimal manifest settings (no advertising id,
 * no SSAID, no ad personalisation). Only screen names (the page names) and a few events whose parameters are ids and category codes; never a user id
 * or anything personal. [start] once with the device's `사용 통계 보내기` choice; without a configured Firebase (no google-services.json) every call is a no-op.
 */
object Analytics {
    private var instance: FirebaseAnalytics? = null

    fun start(context: Context, enabled: Boolean) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        instance = FirebaseAnalytics.getInstance(context).also { it.setAnalyticsCollectionEnabled(enabled) }
    }
    /** The profile switch; collection stops (or resumes) at once. */
    fun setEnabled(enabled: Boolean) { instance?.setAnalyticsCollectionEnabled(enabled) }

    /** A user property (contract analytics-v1 "사용자 속성"); null clears it (sign-out). */
    fun property(name: String, value: String?) { instance?.setUserProperty(name, value) }

    fun screen(name: String) = log(FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to name, FirebaseAnalytics.Param.SCREEN_CLASS to name)
    /** [params] are ids and codes only (e.g. `activity_id`, `type`). */
    fun event(name: String, vararg params: Pair<String, String>) = log(name, *params)

    private fun log(name: String, vararg params: Pair<String, String>) {
        instance?.logEvent(name, Bundle().apply { params.forEach { (key, value) -> putString(key, value) } })
    }
}
