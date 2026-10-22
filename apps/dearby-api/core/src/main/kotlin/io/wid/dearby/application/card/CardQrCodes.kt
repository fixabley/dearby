package io.wid.dearby.application.card

import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PermissionDeniedException
import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 명함 QR 코드(native-v1.md "명함 QR 코드"): 여러 명이 쓰는 코드, 최대 30분, 닫기·새로고침 때 2분 뒤 만료.
// 처음 보는 사람의 조회·저장은 이 코드가 근거다
@Service
class CardQrCodes(private val codes: CardQrCodeRepository, private val cards: CardRepository) {

    data class Issued(val id: UUID, val code: String, val expiresAt: OffsetDateTime)

    // 내 명함이 아니거나 철회됐으면 404
    @Transactional
    fun issue(ownerId: UserId, cardId: UUID): Issued {
        owned(ownerId, cardId)
        val now = now()
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val issued = Issued(UUID.randomUUID(), code, now.plus(LIFETIME))
        codes.create(issued.id, cardId, hash(code), now, issued.expiresAt)
        return issued
    }

    // 닫기·새로고침: 만료를 지금+2분으로 당긴다. 이미 만료됐거나 모르는 코드여도 성공
    @Transactional
    fun close(ownerId: UserId, cardId: UUID, codeId: UUID) {
        owned(ownerId, cardId)
        codes.shorten(cardId, codeId, now().plus(CLOSE_GRACE))
    }

    @Transactional
    fun revokeAll(cardId: UUID) = codes.revokeAll(cardId, now())

    // 코드 없으면 403 CARD_CODE_REQUIRED, 만료·모르는·다른 명함 코드면 403 CARD_CODE_EXPIRED
    @Transactional(readOnly = true)
    fun require(cardId: UUID, code: String?) {
        if (code.isNullOrEmpty()) throw PermissionDeniedException(DETAIL, "card code missing: $cardId", CODE_REQUIRED, "card-code-required")
        if (!codes.isValid(cardId, hash(code), now())) throw PermissionDeniedException(DETAIL, "card code expired or unknown: $cardId", CODE_EXPIRED, "card-code-expired")
    }

    private fun owned(ownerId: UserId, cardId: UUID) {
        cards.find(cardId)?.takeIf { it.ownerId == ownerId } ?: throw NotFoundException("명함을 찾을 수 없습니다", "QR code for missing or foreign card: $cardId by $ownerId")
    }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    companion object {
        const val CODE_REQUIRED = "CARD_CODE_REQUIRED"
        const val CODE_EXPIRED = "CARD_CODE_EXPIRED"
        const val DETAIL = "명함 주인에게 QR을 다시 보여 달라고 해 주세요"
        val LIFETIME: Duration = Duration.ofMinutes(5)
        val CLOSE_GRACE: Duration = Duration.ofMinutes(2)
        private val random = SecureRandom()

        fun hash(code: String): String = MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
