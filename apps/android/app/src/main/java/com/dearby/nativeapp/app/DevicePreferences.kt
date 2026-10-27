package com.dearby.nativeapp.app

import android.content.Context
import androidx.core.content.edit
import com.dearby.nativeapp.features.live.LivePlan
import com.dearby.nativeapp.features.reminder.Reminder

/**
 * What this device remembers for 즐겨찾기 and push (contract "즐겨찾기(하트)·새 모집 푸시 알림"), in a new preferences file of its own: the
 * `사용 통계 보내기` switch and whether push started (the first program heart: the notification permission is asked then, once, and FCM may make a token).
 * Since 2026-10-26 also the 세션 이동 알림 switch and the reminders set as alarms, and the 행사 진행 중 표시 switch with its day plan.
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
    /** The notification permission was asked (Android 13+), at the first program heart or the first session heart, whichever came first; never again. */
    var notificationsAsked: Boolean
        get() = preferences.getBoolean("notificationsAsked", false)
        set(value) = preferences.edit { putBoolean("notificationsAsked", value) }
    /** 설정 › 알림 `세션 이동 알림` (2026-10-26 "찜한 세션 이동 알림"), on until turned off; off cancels every reminder. */
    var sessionRemindersEnabled: Boolean
        get() = preferences.getBoolean("sessionRemindersEnabled", true)
        set(value) = preferences.edit { putBoolean("sessionRemindersEnabled", value) }
    /** The reminders currently set as alarms, so the next plan can cancel the ones that left and a reboot ([SessionReminderBootReceiver]) can set them again. */
    var sessionReminders: List<Reminder>
        get() = preferences.getStringSet("sessionReminders", emptySet()).orEmpty().mapNotNull(Reminder::parse).sortedBy { it.at }
        set(value) = preferences.edit { putStringSet("sessionReminders", value.map { it.toJson() }.toSet()) }
    /** 설정 › 알림 `행사 진행 중 표시` (2026-10-26 "행사 당일 진행 중 알림"), on until turned off; off removes the notification. */
    var liveSessionEnabled: Boolean
        get() = preferences.getBoolean("liveSessionEnabled", true)
        set(value) = preferences.edit { putBoolean("liveSessionEnabled", value) }
    /** The day plan the 행사 진행 중 notification is drawn from (features/live), so a reboot ([SessionReminderBootReceiver]) can arm it again; null without one. */
    var liveSession: LivePlan?
        get() = preferences.getString("liveSession", null)?.let(LivePlan::parse)
        set(value) = preferences.edit { if (value == null) remove("liveSession") else putString("liveSession", value.toJson()) }
    /** The Seoul day analytics-v1 `live_session_shown` was last sent (once a day). */
    var liveSessionShownDay: String?
        get() = preferences.getString("liveSessionShownDay", null)
        set(value) = preferences.edit { putString("liveSessionShownDay", value) }
    /** 오늘 행사 온보딩 (2026-10-27): the Seoul day (`yyyy-MM-dd`) it was last asked, once a day. */
    var todayOnboardingDay: String?
        get() = preferences.getString("todayOnboardingDay", null)
        set(value) = preferences.edit { putString("todayOnboardingDay", value) }
    /** Debug only: 설정 `오늘 행사 온보딩 가짜 날짜` — the nearest coming event day stands in for today. */
    var todayOnboardingFake: Boolean
        get() = preferences.getBoolean("todayOnboardingFake", false)
        set(value) = preferences.edit { putBoolean("todayOnboardingFake", value) }
}
