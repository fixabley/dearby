package io.wid.dearby.application.activity

import io.wid.dearby.domain.ActivityCuration
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.PublicationStatus

// 운영자용 활동 정보 저장소 포트. 구현은 adaptor/persistence
interface ActivityCurationRepository {

    fun findAllByActivityId(activityIds: Collection<Id>): List<ActivityCuration>

    // 바뀐 행이 없으면(활동 없음) false
    fun updatePublicationStatus(activityId: Id, status: PublicationStatus): Boolean
}
