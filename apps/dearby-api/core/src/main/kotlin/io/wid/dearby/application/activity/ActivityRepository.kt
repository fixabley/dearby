package io.wid.dearby.application.activity

import io.wid.dearby.domain.Activity
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.PublicationStatus

// 저장소 포트. 구현은 adaptor/persistence
interface ActivityRepository {

    // id 오름차순으로 after 다음부터 최대 size개
    fun search(condition: ActivitySearch): List<Activity>

    // publicationStatus가 null이면 상태와 상관없이 찾는다
    fun findById(id: Id, publicationStatus: PublicationStatus? = PublicationStatus.PUBLISHED): Activity?
}
