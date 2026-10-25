package io.wid.dearby.domain.push

import io.wid.dearby.domain.UserId

enum class PushPlatform { IOS, ANDROID }

// 푸시 기기 토큰. timeZone은 IANA 이름(그 시간대 아침 8시에 보낸다)
data class PushDevice(val token: String, val userId: UserId, val platform: PushPlatform, val timeZone: String)

