package io.wid.dearby.adaptor.web.v1.cards

import io.wid.dearby.adaptor.web.v1.profile.ContactResponse
import io.wid.dearby.adaptor.web.v1.profile.HistoryResponse
import io.wid.dearby.domain.card.BusinessCard
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.*

// 발행 스냅샷(native-v1.md "명함·지갑"). template은 주인만(공개 조회·지갑에는 싣지 않는다)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CardResponse(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val description: String,
    val template: String?,
    val introduction: String,
    val profileName: String,
    val job: String,
    val contacts: List<ContactResponse>,
    val histories: List<HistoryResponse>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(card: BusinessCard, includeTemplate: Boolean) = CardResponse(
            card.id, card.ownerId, card.name, card.description, card.template.takeIf { includeTemplate }, card.introduction,
            card.profileName, card.job, card.contacts.map(ContactResponse::from), card.histories.map(HistoryResponse::from), card.createdAt, card.updatedAt,
        )
    }
}
