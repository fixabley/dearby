package io.wid.dearby.application.notification

import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.program.ProgramSummaryService
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.notification.UserNotification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 알림 목록(native-v1.md "알림 목록(발견 우상단 종)"). 로그인 사용자만. 프로그램 카드로 묶고 카드는 가장 최근 알림 순
@Service
class NotificationService(private val notifications: NotificationRepository, private val summaries: ProgramSummaryService) {

    @Transactional(readOnly = true)
    fun page(userId: UserId, cursor: Int): NotificationPage {
        if (cursor < 0) throw InvalidInputException("cursor가 올바르지 않습니다")
        val programIds = notifications.programPage(userId, cursor, PAGE_SIZE + 1)
        val page = programIds.take(PAGE_SIZE)
        val items = notifications.findByPrograms(userId, page).groupBy { it.programId }
        val programs = summaries.byIds(page)
        // 게시 활동이 하나도 남지 않은 프로그램은 카드 머리를 만들 수 없어 뺀다
        val groups = page.mapNotNull { id -> programs[id]?.let { NotificationGroup(it, items[id].orEmpty()) } }
        return NotificationPage(groups, if (programIds.size > PAGE_SIZE) cursor + PAGE_SIZE else null)
    }

    @Transactional(readOnly = true)
    fun unreadCount(userId: UserId): Int = notifications.unreadCount(userId)

    // 이미 읽었으면 그대로. 내 알림이 아니거나 없으면 404
    @Transactional
    fun read(userId: UserId, id: Id) {
        if (!notifications.markRead(userId, id, now())) throw NotFoundException("알림을 찾을 수 없습니다", "Notification not found: $id")
    }

    @Transactional
    fun readAll(userId: UserId) = notifications.markAllRead(userId, now())

    private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

    companion object {
        const val PAGE_SIZE = 20
    }
}

data class NotificationGroup(val program: ProgramSummary, val items: List<UserNotification>) {
    val unread: Int get() = items.count { it.readAt == null }
}

data class NotificationPage(val groups: List<NotificationGroup>, val nextCursor: Int?)
