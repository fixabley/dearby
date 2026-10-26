package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.History
import java.time.OffsetDateTime
import java.util.*

// 발행(또는 고치기) 시점 스냅샷. 프로필을 고쳐도 바뀌지 않는다. 고치기(PUT)는 같은 id로 다시 만들고 updatedAt만 바뀐다. introduction은 프로필 자기소개를 복사한 공개 본문(마크다운), template은 폐지 예정(2026-10-26 뒤 새 스냅샷은 빈 문자열). 이력의 evidence는 비어 있다
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
    // 복사해 둔 사진의 버전(프로필 사진 updated_at). 사진이 없으면 null(native-v1.md "프로필 사진")
    val photoUpdatedAt: OffsetDateTime? = null,
)
