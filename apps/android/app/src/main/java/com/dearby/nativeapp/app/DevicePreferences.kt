package com.dearby.nativeapp.app

import android.content.Context
import androidx.core.content.edit

/**
 * What this device remembers for 즐겨찾기 and push (contract "즐겨찾기(하트)·새 모집 푸시 알림"), in a new preferences file of its own: the
 * `사용 통계 보내기` switch and whether push started (the first program heart: the notification permission is asked then, once, and FCM may make a token).
 */
class DevicePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("dearby.favorites", Context.MODE_PRIVATE)
    /** 설정 `사용 통계 보내기` (2026-10-25), on until turned off. */
    var analyticsEnabled: Boolean
        get() = preferences.getBoolean("analyticsEnabled", true)
        set(value) = preferences.edit { putBoolean("analyticsEnabled", value) }
    /** 광고 동의 시트의 거부·설정 스위치 끔 (contract "광고 목적 개인정보 제3자 제공 동의(선택)"): remembered per account and version, never sent. */
    fun adConsentDeclined(account: String, version: String) = "$account/$version" in preferences.getStringSet("adConsentDeclined", emptySet()).orEmpty()
    fun declineAdConsent(account: String, version: String) =
        preferences.edit { putStringSet("adConsentDeclined", preferences.getStringSet("adConsentDeclined", emptySet()).orEmpty() + "$account/$version") }
    /** 발견 `프로그램 | 이벤트` (2026-10-26): the last choice, `program` until changed. */
    var discoveryKind: String
        get() = preferences.getString("discoveryKind", null) ?: "program"
        set(value) = preferences.edit { putString("discoveryKind", value) }
    var pushStarted: Boolean
        get() = preferences.getBoolean("pushStarted", false)
        set(value) = preferences.edit { putBoolean("pushStarted", value) }
}
