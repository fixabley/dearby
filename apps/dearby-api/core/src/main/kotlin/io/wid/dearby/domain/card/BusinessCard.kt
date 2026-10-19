package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.History
import java.time.OffsetDateTime
import java.util.*

// 발행(또는 고치기) 시점 스냅샷. 프로필을 고쳐도 바뀌지 않는다. 고치기(PUT)는 같은 id로 다시 만들고 updatedAt만 바뀐다. template은 보낸 마크다운 템플릿, introduction은 그것을 채운 공개 본문. 이력의 evidence는 비어 있다
data class BusinessCard(
    val id: UUID,
    val ownerId: UserId,
    val name: String,
    val description: String,
    val template: String,
    val introduction: String,
    val profileName: String,
    val job: String,
    val contacts: List<Contact>,
    val histories: List<History>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
