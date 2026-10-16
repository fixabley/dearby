package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Profile
import io.wid.dearby.domain.profile.ProfileVersion
import java.time.OffsetDateTime

// 프로필 스냅샷(profile_versions). 구현은 adaptor/persistence
interface ProfileVersionRepository {

    fun append(profile: Profile)

    // 최신순. after는 이전 페이지 마지막 version
    fun list(userId: UserId, after: Int?, size: Int): List<Pair<Int, OffsetDateTime>>

    fun find(userId: UserId, version: Int): ProfileVersion?
}
