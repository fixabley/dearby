package io.wid.dearby.application.card

import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PermissionDeniedException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.CardBlock
import io.wid.dearby.domain.card.Receipt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 받은 명함(지갑, native-v1.md "명함 교환"). 같은 사용자+명함은 Receipt 하나. 저장은 QR의 공개 주소에서 앱이 부르고,
// 맞교환은 받은 사람이 자기 명함을 주인 지갑에 넣는 것이다. 회수는 batch(RecallService)가 한다
@Service
class WalletService(private val wallet: WalletRepository, private val cards: CardRepository, private val codes: CardQrCodes, private val safety: CardSafetyRepository) {

    data class Saved(val receipt: Receipt, val created: Boolean)

    @Transactional(readOnly = true)
    fun list(userId: UserId): List<Receipt> = wallet.receipts(userId)

    // 없는·철회된 명함 404, 자기 명함 409 OWN_CARD. 이미 있으면 받은 날은 두고 활동만 비어 있을 때 채운다. 모르는 활동 id는 null로 본다.
    // 처음 저장(회수 뒤 다시 저장 포함)은 유효한 QR 코드가 필요하다(native-v1.md "명함 QR 코드", 아니면 403)
    @Transactional
    fun save(userId: UserId, cardId: UUID, activityId: Id?, code: String? = null): Saved {
        val card = cards.find(cardId) ?: throw NotFoundException("명함을 찾을 수 없습니다", "Card not found for wallet: $cardId")
        if (card.ownerId == userId) throw ConflictException("내 명함이에요", "own card saved to wallet: $cardId", code = OWN_CARD)
        if (safety.isBlocked(userId, null, card.ownerId)) throw ConflictException(BLOCKED_DETAIL, "blocked owner's card saved to wallet: $cardId", code = BLOCKED)
        val activity = activityId?.takeIf(wallet::activityExists)
        val existing = wallet.findReceipt(userId, cardId)
        if (existing != null) {
            if (existing.context.activityId == null && activity != null) wallet.setActivity(existing.id, activity)
            return Saved(wallet.findReceipt(userId, cardId)!!, created = false)
        }
        codes.require(cardId, code)
        wallet.createReceipt(UUID.randomUUID(), userId, cardId, activity, OffsetDateTime.now(ZoneOffset.UTC))
        return Saved(wallet.findReceipt(userId, cardId)!!, created = true)
    }

    // 받은 명함(cardId)의 주인에게 내 명함(myCardId)을 같은 활동으로 건넨다. 받은 적이 없으면 403, 내 명함이 아니면 404. 이미 건넸으면 그대로
    @Transactional
    fun reciprocate(userId: UserId, cardId: UUID, myCardId: UUID) {
        val received = wallet.findReceipt(userId, cardId) ?: throw PermissionDeniedException("받은 명함이 아닙니다", "reciprocate without receipt: $cardId")
        val mine = cards.find(myCardId)?.takeIf { it.ownerId == userId } ?: throw NotFoundException("내 명함을 찾을 수 없습니다", "reciprocate with missing or foreign card: $myCardId")
        val owner = received.card.ownerId
        // 상대가 나를 차단했으면 알리지 않고 넣지 않는다(native-v1.md "명함 신고·차단")
        if (safety.isBlocked(owner, null, userId)) return
        if (wallet.findReceipt(owner, mine.id) == null) wallet.createReceipt(UUID.randomUUID(), owner, mine.id, received.context.activityId, OffsetDateTime.now(ZoneOffset.UTC))
    }

    // 차단(native-v1.md "명함 신고·차단"): 그 명함 주인의 명함을 내 지갑에서 모두 회수하고 기록한다. 없는·철회된 명함 404, 내 명함 400. 이미 차단했으면 그 기록
    @Transactional
    fun block(userId: UserId, cardId: UUID): CardBlock {
        val card = cards.find(cardId) ?: throw NotFoundException("명함을 찾을 수 없습니다", "block for missing card: $cardId")
        if (card.ownerId == userId) throw InvalidInputException("내 명함은 차단할 수 없어요", "block own card: $cardId")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        wallet.recallFromOwner(userId, card.ownerId, now)
        return safety.block(userId, null, card.ownerId, card.profileName, now)
    }

    @Transactional(readOnly = true)
    fun blocks(userId: UserId): List<CardBlock> = safety.blocks(userId)

    // 해제해도 회수한 명함은 돌아오지 않는다. 내 차단이 아니면 404
    @Transactional
    fun unblock(userId: UserId, id: UUID) {
        if (!safety.unblock(userId, id)) throw NotFoundException("차단 기록을 찾을 수 없습니다", "unblock missing: $id by $userId")
    }

    companion object {
        const val OWN_CARD = "OWN_CARD"
        const val BLOCKED = "BLOCKED"
        const val BLOCKED_DETAIL = "차단한 사람의 명함이에요"
    }
}
