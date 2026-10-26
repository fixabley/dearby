package io.wid.dearby.application.card

import io.wid.dearby.application.profile.ProfileRepository
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PermissionDeniedException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.BusinessCard
import io.wid.dearby.domain.profile.ApprovalStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 명함 발행·고치기·조회·철회(native-v1.md "명함·지갑"). 발행은 프로필의 공개 선택분을 복사한 스냅샷이다
@Service
class CardService(private val cards: CardRepository, private val profiles: ProfileRepository, private val wallet: WalletRepository, private val codes: CardQrCodes) {

    @Transactional
    fun publish(ownerId: UserId, name: String, description: String, contactIds: List<UUID>, historyIds: List<UUID>, includePhoto: Boolean = true, includeIntroLink: Boolean = true): BusinessCard {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val card = snapshot(UUID.randomUUID(), ownerId, name, description, contactIds, historyIds, includeIntroLink, createdAt = now, updatedAt = now)
        cards.create(card)
        return card.copy(photoUpdatedAt = cards.snapshotPhoto(card.id, ownerId, includePhoto))
    }

    // 명함 고치기(native-v1.md "명함 고치기는 같은 명함을 수정"): 같은 id·createdAt으로 현재 프로필 기준 스냅샷을 다시 만든다.
    // 없거나 철회됐거나 남의 명함이면 모두 404(지우기의 403과 다르다)
    @Transactional
    fun update(ownerId: UserId, id: UUID, name: String, description: String, contactIds: List<UUID>, historyIds: List<UUID>, includePhoto: Boolean = true, includeIntroLink: Boolean = true): BusinessCard {
        val existing = cards.find(id)?.takeIf { it.ownerId == ownerId } ?: throw NotFoundException("명함을 찾을 수 없습니다", "Card not found or not owned: $id by $ownerId")
        val card = snapshot(id, ownerId, name, description, contactIds, historyIds, includeIntroLink, createdAt = existing.createdAt, updatedAt = OffsetDateTime.now(ZoneOffset.UTC))
        if (!cards.update(card)) throw NotFoundException("명함을 찾을 수 없습니다", "Card revoked while updating: $id")
        return card.copy(photoUpdatedAt = cards.snapshotPhoto(id, ownerId, includePhoto))
    }

    // POST·PUT 공통 검증과 스냅샷 만들기. 프로필의 공개 선택분과 소개 링크(넣기를 켰을 때)를 복사한다(2026-10-26 "소개 링크·소개 글 폐지").
    // template·introduction 열은 옛 앱 응답 호환으로만 남아 새 스냅샷에는 빈 문자열이다
    private fun snapshot(id: UUID, ownerId: UserId, name: String, description: String, contactIds: List<UUID>, historyIds: List<UUID>, includeIntroLink: Boolean, createdAt: OffsetDateTime, updatedAt: OffsetDateTime): BusinessCard {
        val profile = profiles.find(ownerId) ?: throw InvalidInputException("프로필을 먼저 만들어 주세요")
        val cardName = text(name, "명함 이름", MAX_NAME)
        val cardDescription = multiline(description, "설명")
        if (contactIds.toSet().size != contactIds.size || historyIds.toSet().size != historyIds.size) throw InvalidInputException("같은 항목을 두 번 골랐습니다")
        val contactById = profile.contacts.associateBy { it.id }
        val historyById = profile.histories.associateBy { it.id }
        val contacts = contactIds.map { contactById[it] ?: throw InvalidInputException("내 프로필에 없는 연락처입니다", "Contact not in profile: $it") }
        val histories = historyIds.map { id ->
            val history = historyById[id] ?: throw InvalidInputException("내 프로필에 없는 이력입니다", "History not in profile: $id")
            if (history.approval != ApprovalStatus.APPROVED) throw InvalidInputException("관리자 승인 전인 이력은 명함에 넣을 수 없습니다", "History not approved: $id")
            history.copy(evidence = emptyList())
        }
        return BusinessCard(
            id = id,
            ownerId = ownerId,
            name = cardName,
            description = cardDescription,
            template = "",
            introLink = profile.introLink.takeIf { includeIntroLink },
            profileName = profile.name,
            job = profile.jobTitle,
            contacts = contacts,
            histories = histories,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    @Transactional(readOnly = true)
    fun list(ownerId: UserId): List<BusinessCard> = cards.listByOwner(ownerId)

    @Transactional(readOnly = true)
    fun get(id: UUID): BusinessCard = cards.find(id) ?: throw NotFoundException("명함을 찾을 수 없습니다", "Card not found: $id")

    // GET /v1/cards/:id(native-v1.md "명함 QR 코드"): 주인, 회수 전 Receipt를 가진 계정, 회수 전 게스트 저장(guestHolds), 유효한 QR 코드 중 하나면 본다
    @Transactional(readOnly = true)
    fun view(id: UUID, viewer: UserId?, guestHolds: Boolean, code: String?): BusinessCard {
        val card = get(id)
        val holder = viewer != null && (card.ownerId == viewer || wallet.findReceipt(viewer, id) != null)
        if (!holder && !guestHolds) codes.require(id, code)
        return card
    }

    // 명함 사진(native-v1.md "프로필 사진"): 명함 조회와 같은 근거로 본다. 사진 없는 명함은 404
    @Transactional(readOnly = true)
    fun photo(id: UUID, viewer: UserId?, guestHolds: Boolean, code: String?): ByteArray {
        view(id, viewer, guestHolds, code)
        return cards.photo(id) ?: throw NotFoundException("명함 사진이 없습니다", "Card photo not found: $id")
    }

    @Transactional
    fun revoke(ownerId: UserId, id: UUID) {
        val card = get(id)
        if (card.ownerId != ownerId) throw PermissionDeniedException("내 명함만 지울 수 있습니다", "Card $id not owned by $ownerId")
        cards.revoke(id)
        codes.revokeAll(id)
    }

    private fun text(value: String, field: String, max: Int): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) throw InvalidInputException("${field}을(를) 입력해 주세요")
        if (trimmed.length > max || trimmed.any { it.isISOControl() }) throw InvalidInputException("${field}이(가) 올바르지 않습니다")
        return trimmed
    }

    private fun multiline(value: String, field: String): String {
        val trimmed = value.trim()
        if (trimmed.length > MAX_TEXT) throw InvalidInputException("${field}은(는) ${MAX_TEXT}자까지입니다")
        if (trimmed.any { it.isISOControl() && it != '\n' && it != '\t' && it != '\r' }) throw InvalidInputException("${field}에 쓸 수 없는 문자가 있습니다")
        return trimmed
    }

    private companion object {
        const val MAX_NAME = 200
        const val MAX_TEXT = 10_000
    }
}
