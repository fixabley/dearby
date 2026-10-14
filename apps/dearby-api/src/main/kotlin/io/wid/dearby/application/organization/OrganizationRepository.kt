package io.wid.dearby.application.organization

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Organization

// 저장소 포트. 구현은 adaptor/persistence
interface OrganizationRepository {

    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Organization>
}
