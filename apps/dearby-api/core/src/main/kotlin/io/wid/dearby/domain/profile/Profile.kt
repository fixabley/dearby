package io.wid.dearby.domain.profile

import io.wid.dearby.domain.UserId
import java.time.OffsetDateTime

// 내 프로필(native-v1.md "프로필·명함 v2"). introduction은 마크다운 템플릿이고 명함을 발행할 때 채운다.
// 저장마다 version이 1 오르고 같은 내용이 profile_versions에 남는다
data class Profile(
    val userId: UserId,
    val name: String,
    val job: String,
    val introduction: String,
    val contacts: List<Contact>,
    val histories: List<History>,
    val version: Int,
    val updatedAt: OffsetDateTime,
)
