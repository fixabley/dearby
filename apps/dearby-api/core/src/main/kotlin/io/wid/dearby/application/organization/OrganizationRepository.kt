package io.wid.dearby.application.organization

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Organization

// 저장소 포트. 구현은 adaptor/persistence
interface OrganizationRepository {

    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Organization>

    // q는 이름에서 대소문자 없이 부분 일치. id 오름차순으로 after 다음부터 최대 size개
    fun search(q: String?, after: Id?, size: Int): List<Organization>

    fun create(name: String, parentOrganizationId: Id?): Id
}
