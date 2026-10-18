package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.History
import java.time.OffsetDateTime
import java.util.*

// 발행 시점 스냅샷. 프로필을 고쳐도 바뀌지 않는다. template은 보낸 마크다운 템플릿, introduction은 그것을 채운 공개 본문. 이력의 evidence는 비어 있다
// webTemplate·webIntroduction은 선택 웹 버전(native-v1.md "웹 버전"): 함께 있거나 함께 null
data class BusinessCard(
    val id: UUID,
    val ownerId: UserId,
    val name: String,
    val description: String,
    val template: String,
    val introduction: String,
    val webTemplate: String? = null,
    val webIntroduction: String? = null,
    val profileName: String,
    val job: String,
    val contacts: List<Contact>,
    val histories: List<History>,
    val createdAt: OffsetDateTime,
)
