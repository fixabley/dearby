package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.History
import io.wid.dearby.domain.profile.Profile
import java.util.*

// 저장소 포트. 구현은 adaptor/persistence. 이력의 활동 정보 이름(ActivityInfoRef.name)은 읽을 때 활동 정보에서 채운다
interface ProfileRepository {

    fun find(userId: UserId): Profile?

    // 프로필 행을 만들거나 바꾸고 연락처·이력을 전부 주어진 것으로 바꾼다(상태 값은 호출하는 쪽이 정한다)
    fun save(profile: Profile)

    fun findHistory(userId: UserId, historyId: UUID): History?
}
