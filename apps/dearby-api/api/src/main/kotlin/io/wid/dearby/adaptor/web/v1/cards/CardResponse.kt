package io.wid.dearby.adaptor.web.v1.cards

import io.wid.dearby.adaptor.web.v1.profile.ContactResponse
import io.wid.dearby.adaptor.web.v1.profile.HistoryResponse
import io.wid.dearby.domain.card.BusinessCard
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.*

// 발행 스냅샷(native-v1.md "명함·지갑"). template은 주인만(공개 조회·지갑에는 싣지 않는다). 2026-10-26 폐지 예정: 옛 앱 호환으로 남기며 새 명함은 빈 문자열
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CardResponse(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val description: String,
    val template: String?,
    // 2026-10-26 폐지 예정(소개 글 폐지): 옛 앱 호환으로 늘 빈 문자열. 이미 발행된 명함의 옛 본문도 싣지 않는다
    val introduction: String,
    @get:JsonInclude(JsonInclude.Include.ALWAYS) val introLink: String?,
    val profileName: String,
    val job: String,
    val contacts: List<ContactResponse>,
    val histories: List<HistoryResponse>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    // "/v1/cards/:id/photo?v=<ms>" 또는 null(native-v1.md "프로필 사진")
    @get:JsonInclude(JsonInclude.Include.ALWAYS) val photoUrl: String?,
) {
    companion object {
        fun from(card: BusinessCard, includeTemplate: Boolean) = CardResponse(
            card.id, card.ownerId, card.name, card.description, card.template.takeIf { includeTemplate }, "", card.introLink,
            card.profileName, card.job, card.contacts.map(ContactResponse::from), card.histories.map(HistoryResponse::from), card.createdAt, card.updatedAt,
            card.photoUpdatedAt?.let { "/v1/cards/${card.id}/photo?v=${it.toInstant().toEpochMilli()}" },
        )
    }
}
