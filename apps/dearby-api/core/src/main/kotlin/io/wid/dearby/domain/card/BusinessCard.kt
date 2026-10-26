package io.wid.dearby.domain.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.History
import java.time.OffsetDateTime
import java.util.*

// 발행(또는 고치기) 시점 스냅샷. 프로필을 고쳐도 바뀌지 않는다. 고치기(PUT)는 같은 id로 다시 만들고 updatedAt만 바뀐다. introLink는 프로필 소개 링크의 복사본(넣기를 끄면 null). 소개 글 본문(introduction)은 2026-10-26 폐지로 읽지도 쓰지도 않는다(열만 남음), template도 폐지 예정(2026-10-26 뒤 새 스냅샷은 빈 문자열). 이력의 evidence는 비어 있다
data class BusinessCard(
    val id: UUID,
    val ownerId: UserId,
    val name: String,
    val description: String,
    val template: String,
    val introLink: String?,
    val profileName: String,
    val job: String,
    val contacts: List<Contact>,
    val histories: List<History>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    // 복사해 둔 사진의 버전(프로필 사진 updated_at). 사진이 없으면 null(native-v1.md "프로필 사진")
    val photoUpdatedAt: OffsetDateTime? = null,
)
