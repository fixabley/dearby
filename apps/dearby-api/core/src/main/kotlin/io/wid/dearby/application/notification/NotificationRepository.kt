package io.wid.dearby.application.notification

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.notification.NotificationDraft
import io.wid.dearby.domain.notification.UserNotification
import java.time.LocalDate
import java.time.OffsetDateTime

// 저장소 포트(알림 목록·보낸 푸시). 구현은 adaptor/persistence. users cascade
interface NotificationRepository {

    // (사용자, 활동, 종류, 날짜)마다 하나. NEW는 같은 활동에 이미 있으면 넣지 않는다
    fun saveAll(userId: UserId, drafts: List<NotificationDraft>, at: OffsetDateTime)

    // 푸시 근거: 그 날짜의 알림 + since 뒤에 만든 NEW(다른 날짜로 만들어진 신규도 기기마다 한 번)
    fun forPush(userId: UserId, date: LocalDate, newSince: OffsetDateTime): List<UserNotification>
    fun delivered(notificationIds: Collection<Id>, deviceToken: String): Set<Id>
    fun saveDeliveries(notificationIds: Collection<Id>, deviceToken: String, at: OffsetDateTime)

    // 묶음(프로그램)을 가장 최근 알림 순으로 cursor부터 size개. 그 프로그램들의 알림 전부(최근 순)
    fun programPage(userId: UserId, cursor: Int, size: Int): List<Id>
    fun findByPrograms(userId: UserId, programIds: Collection<Id>): List<UserNotification>
    fun unreadCount(userId: UserId): Int
    fun markRead(userId: UserId, id: Id, at: OffsetDateTime): Boolean
    fun markAllRead(userId: UserId, at: OffsetDateTime)
    fun deleteBefore(at: OffsetDateTime): Int
}
