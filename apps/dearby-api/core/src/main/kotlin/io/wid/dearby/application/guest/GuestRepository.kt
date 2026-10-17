package io.wid.dearby.application.guest

import java.time.OffsetDateTime
import java.util.*

// 게스트 세션·저장·세션 잇기 저장소 포트. 구현은 adaptor/persistence
interface GuestRepository {

    fun sessionExists(digest: String): Boolean

    fun sessionCount(): Int

    fun createSession(digest: String)

    fun deleteSession(digest: String): Boolean

    // 철회된 명함의 저장 참조를 지운다(용량 반환)
    fun pruneRevoked(digest: String)

    fun hasCard(digest: String, cardId: UUID): Boolean

    fun cardCount(digest: String): Int

    fun addCard(digest: String, cardId: UUID)

    fun removeCard(digest: String, cardId: UUID)

    fun hasShare(digest: String, shareId: UUID): Boolean

    fun shareCount(digest: String, cardId: UUID): Int

    fun addShare(digest: String, cardId: UUID, shareId: UUID, savedAt: OffsetDateTime)

    fun wallet(digest: String): GuestWallet

    // 세션당 하나. 있으면 바꾼다
    fun putHandoff(handoff: GuestHandoff)

    fun findHandoff(digest: String): GuestHandoff?

    fun markHandoffUsed(digest: String, usedAt: OffsetDateTime)

    // 만료된 암호문을 지운다
    fun clearExpiredHandoffs(now: OffsetDateTime)
}
