package io.wid.dearby.application.card

import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PermissionDeniedException
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.BusinessCard
import io.wid.dearby.domain.card.CardShare
import io.wid.dearby.domain.card.SharedActivity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 공유 기록. 활동은 공유 시점의 {id, title} 복사본이며 참가 증명이 아니다
@Service
class ShareService(private val shares: ShareRepository, private val cards: CardRepository, private val activities: ActivityRepository) {

    @Transactional
    fun create(ownerId: UserId, cardId: UUID, activityIds: List<Long>): CardShare {
        val card = cards.find(cardId) ?: throw NotFoundException("명함을 찾을 수 없습니다", "Card not found: $cardId")
        if (card.ownerId != ownerId) throw PermissionDeniedException("내 명함만 공유할 수 있습니다", "Card $cardId not owned by $ownerId")
        if (activityIds.size > MAX_ACTIVITIES || activityIds.toSet().size != activityIds.size) throw InvalidInputException("활동은 ${MAX_ACTIVITIES}개까지 한 번씩만 고를 수 있습니다")
        val copied = activityIds.map { id ->
            val activity = activities.findById(id, PublicationStatus.PUBLISHED) ?: throw InvalidInputException("활동을 찾을 수 없습니다", "Activity not found: $id")
            SharedActivity(activity.id, activity.name)
        }
        val share = CardShare(UUID.randomUUID(), cardId, copied, OffsetDateTime.now(ZoneOffset.UTC))
        shares.create(share)
        return share
    }

    // 철회된 명함의 공유도 없는 것으로 본다
    @Transactional(readOnly = true)
    fun get(shareId: UUID): Pair<CardShare, BusinessCard> {
        val share = shares.find(shareId) ?: throw NotFoundException("공유를 찾을 수 없습니다", "Share not found: $shareId")
        val card = cards.find(share.cardId) ?: throw NotFoundException("공유를 찾을 수 없습니다", "Card revoked for share: $shareId")
        return share to card
    }

    private companion object {
        const val MAX_ACTIVITIES = 10
    }
}
