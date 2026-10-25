package io.wid.dearby.domain.notification

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import java.time.LocalDate
import java.time.OffsetDateTime

// 알림 종류(native-v1.md "알림 목록", "프로그램·활동별 알림 수준"). NEW는 신규(한 번만), 신청일은 APPLICATION_START·DEADLINE_EVE·DEADLINE,
// 그 밖의 일정(알림 수준 전체)은 그 일정의 종류(발표·면접·행사, 활동 기간 시작은 ACTIVITY_START)
enum class NotificationKind { NEW, APPLICATION_START, DEADLINE_EVE, DEADLINE, ANNOUNCEMENT, INTERVIEW, EVENT, ACTIVITY_START }

data class UserNotification(
    val id: Id,
    val userId: UserId,
    val activityId: Id,
    val activityName: String,
    val programId: Id,
    val kind: NotificationKind,
    val localDate: LocalDate,
    val createdAt: OffsetDateTime,
    val readAt: OffsetDateTime?,
)

// 쌓을 알림 거리 한 건
data class NotificationDraft(val activityId: Id, val programId: Id, val kind: NotificationKind, val localDate: LocalDate)
