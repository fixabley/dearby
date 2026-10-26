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
    /** 발견 상태 dropdown (2026-10-26, contract "활동 상태"): the chosen groups' codes, comma-joined; `available` (지원 가능) until changed. */
    var discoveryStatus: String
        get() = preferences.getString("discoveryStatus", null) ?: "available"
        set(value) = preferences.edit { putString("discoveryStatus", value) }
    /** 발견 종류 dropdown (2026-10-26) per kind (`program`·`event`): the chosen category codes; empty (전체) until changed. */
    fun discoveryCategories(kind: String): Set<String> = preferences.getString("discoveryCategories.$kind", null).orEmpty().split(',').filter { it.isNotEmpty() }.toSet()
    fun setDiscoveryCategories(kind: String, codes: Set<String>) = preferences.edit { putString("discoveryCategories.$kind", codes.joinToString(",")) }
    /** 지난 선발 단계 자동 통과 제안 (2026-10-26): the activities already asked, once each whatever the answer. */
    fun autopassAsked(id: String) = id in preferences.getStringSet("autopassAsked", emptySet()).orEmpty()
    fun rememberAutopassAsked(id: String) = preferences.edit { putStringSet("autopassAsked", preferences.getStringSet("autopassAsked", emptySet()).orEmpty() + id) }
    var pushStarted: Boolean
        get() = preferences.getBoolean("pushStarted", false)
        set(value) = preferences.edit { putBoolean("pushStarted", value) }
}
