package io.wid.dearby.domain.profile

import java.time.OffsetDateTime

// 저장 시점의 프로필 전체 스냅샷
data class ProfileVersion(val version: Int, val createdAt: OffsetDateTime, val profile: Profile)
