package io.wid.dearby.application.guest

import io.wid.dearby.domain.card.ExchangeRef
import java.time.OffsetDateTime
import java.util.*

// 게스트 세션·저장·세션 잇기 저장소 포트. 구현은 adaptor/persistence. 회수된 행(recalled_at)은 목록·개수에서 뺀다
interface GuestRepository {

    fun sessionExists(digest: String): Boolean

    fun sessionCount(): Int

    fun createSession(digest: String)

    fun deleteSession(digest: String): Boolean

    // 철회된 명함의 저장 참조를 지운다(용량 반환)
    fun pruneRevoked(digest: String)

    fun hasCard(digest: String, cardId: UUID): Boolean

    fun cardCount(digest: String): Int

    // 회수됐던 같은 세션+명함 행이 있으면 되살린다(저장일·맥락을 새로 적는다). 맥락은 호출 전에 판정한다(ExchangeContextRepository)
    fun addCard(digest: String, cardId: UUID, context: ExchangeRef)

    // 게스트 차단: 그 세션에서 그 주인의 명함 저장을 모두 회수한다
    fun recallFromOwner(digest: String, ownerId: UUID, now: OffsetDateTime)

    // 저장일이 savedBefore보다 이른 저장을 회수한다(비로그인은 맞교환이 없다). 회수한 행 수
    fun recallExpired(savedBefore: OffsetDateTime, now: OffsetDateTime): Int

    fun removeCard(digest: String, cardId: UUID)

    // 계정 합치기(native-v1.md "웹 로그인"): 세션의 회수 전 저장을 모두 회수한다(행은 남긴다). 회수한 행 수
    fun recallAll(digest: String, now: OffsetDateTime): Int

    fun wallet(digest: String): GuestWallet

    // 세션당 하나. 있으면 바꾼다
    fun putHandoff(handoff: GuestHandoff)

    fun findHandoff(digest: String): GuestHandoff?

    fun markHandoffUsed(digest: String, usedAt: OffsetDateTime)

    // 만료된 암호문을 지운다
    fun clearExpiredHandoffs(now: OffsetDateTime)
}
