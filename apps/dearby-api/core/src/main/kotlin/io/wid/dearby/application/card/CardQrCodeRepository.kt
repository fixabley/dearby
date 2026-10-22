package io.wid.dearby.application.card

import java.time.OffsetDateTime
import java.util.*

// 명함 QR 코드 저장소(native-v1.md "명함 QR 코드"). 코드 원문은 없고 SHA-256 hex만. 구현은 adaptor/persistence
interface CardQrCodeRepository {

    fun create(id: UUID, cardId: UUID, codeHash: String, createdAt: OffsetDateTime, expiresAt: OffsetDateTime)

    // 그 명함의 코드 만료를 latest로 당긴다(이미 더 이르면 그대로). 모르는 id는 아무 일도 없다
    fun shorten(cardId: UUID, id: UUID, latest: OffsetDateTime)

    // 명함 철회: 그 명함의 코드를 모두 끝낸다
    fun revokeAll(cardId: UUID, now: OffsetDateTime)

    // 그 명함의 것이고 철회되지 않았고 now가 만료 전이면 true
    fun isValid(cardId: UUID, codeHash: String, now: OffsetDateTime): Boolean
}
