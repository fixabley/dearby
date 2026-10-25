package io.wid.dearby.batch.push

import io.wid.dearby.domain.push.PushPlatform

// 푸시 발송 포트(native-v1.md "푸시 알림"). 구현은 HttpPushSender(APNs 토큰 인증 HTTP/2, FCM HTTP v1). 테스트는 가짜로 바꾼다
interface PushSender {

    // 그 플랫폼으로 보낼 수 없으면(인증 정보 없음) 이유, 보낼 수 있으면 null
    fun problem(platform: PushPlatform): String?

    fun send(platform: PushPlatform, token: String, message: PushMessage): PushResult
}

// activityId가 있으면 활동 상세(url = 웹 /activities/{id}), 없으면 묶음 알림(url = 앱 즐겨찾기 캘린더)
data class PushMessage(val title: String, val body: String, val activityId: Long?, val url: String)

// UNREGISTERED: APNs 410·FCM UNREGISTERED. 그 토큰을 지운다
enum class PushResult { SENT, UNREGISTERED, FAILED }
