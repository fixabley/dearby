package io.wid.dearby.domain.push

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import java.time.LocalDate

enum class PushPlatform { IOS, ANDROID }

// 푸시 기기 토큰. timeZone은 IANA 이름(그 시간대 아침 8시에 보낸다)
data class PushDevice(val token: String, val userId: UserId, val platform: PushPlatform, val timeZone: String)

// 알림 거리 종류(native-v1.md "프로그램·활동별 알림 수준"). 기기마다 NEW_ACTIVITY는 날짜와 상관없이 한 번, 나머지는 그날 한 번
enum class PushKind { NEW_ACTIVITY, APPLICATION_START, DEADLINE_EVE, DEADLINE, SCHEDULE }

// date는 그 기기 시간대의 날짜
data class PushDelivery(val userId: UserId, val deviceToken: String, val activityId: Id, val kind: PushKind, val date: LocalDate)
