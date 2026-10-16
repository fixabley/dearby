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

// 명함 발행·조회·철회(native-v1.md "명함·공유·지갑"). 발행은 프로필의 공개 선택분을 복사한 스냅샷이다
@Service
class CardService(private val cards: CardRepository, private val profiles: ProfileRepository) {

    @Transactional
    fun publish(ownerId: UserId, name: String, description: String, template: String, contactIds: List<UUID>, historyIds: List<UUID>): BusinessCard {
        val profile = profiles.find(ownerId) ?: throw InvalidInputException("프로필을 먼저 만들어 주세요")
        val cardName = text(name, "명함 이름", MAX_NAME)
        val cardDescription = multiline(description, "설명")
        val cardTemplate = multiline(template, "템플릿")
        if (contactIds.toSet().size != contactIds.size || historyIds.toSet().size != historyIds.size) throw InvalidInputException("같은 항목을 두 번 골랐습니다")
        val contactById = profile.contacts.associateBy { it.id }
        val historyById = profile.histories.associateBy { it.id }
        val contacts = contactIds.map { contactById[it] ?: throw InvalidInputException("내 프로필에 없는 연락처입니다", "Contact not in profile: $it") }
        val histories = historyIds.map { id ->
            val history = historyById[id] ?: throw InvalidInputException("내 프로필에 없는 이력입니다", "History not in profile: $id")
            if (history.approval != ApprovalStatus.APPROVED) throw InvalidInputException("관리자 승인 전인 이력은 명함에 넣을 수 없습니다", "History not approved: $id")
            history.copy(evidence = emptyList())
        }
        val card = BusinessCard(
            id = UUID.randomUUID(),
            ownerId = ownerId,
            name = cardName,
            description = cardDescription,
            template = cardTemplate,
            introduction = TemplateRenderer.render(cardTemplate, profile, contacts),
            profileName = profile.name,
            job = profile.job,
            contacts = contacts,
            histories = histories,
            createdAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
        cards.create(card)
        return card
    }

    @Transactional(readOnly = true)
    fun list(ownerId: UserId): List<BusinessCard> = cards.listByOwner(ownerId)

    @Transactional(readOnly = true)
    fun get(id: UUID): BusinessCard = cards.find(id) ?: throw NotFoundException("명함을 찾을 수 없습니다", "Card not found: $id")

    @Transactional
    fun revoke(ownerId: UserId, id: UUID) {
        val card = get(id)
        if (card.ownerId != ownerId) throw PermissionDeniedException("내 명함만 지울 수 있습니다", "Card $id not owned by $ownerId")
        cards.revoke(id)
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
