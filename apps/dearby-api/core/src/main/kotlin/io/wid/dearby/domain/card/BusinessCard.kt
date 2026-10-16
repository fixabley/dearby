package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.History
import java.time.OffsetDateTime
import java.util.*

// 발행 시점 스냅샷. 프로필을 고쳐도 바뀌지 않는다. introduction은 템플릿을 채운 결과, 이력의 evidence는 비어 있다
data class BusinessCard(
    val id: UUID,
    val ownerId: UserId,
    val name: String,
    val description: String,
    val profileName: String,
    val job: String,
    val introduction: String,
    val contacts: List<Contact>,
    val histories: List<History>,
    val createdAt: OffsetDateTime,
)
