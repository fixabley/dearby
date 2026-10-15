package io.wid.dearby.application.organization

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Organization

// 저장소 포트. 구현은 adaptor/persistence
interface OrganizationRepository {

    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Organization>

    // q는 이름에서 대소문자 없이 부분 일치. parentId가 있으면 그 조직의 하위만, rootsOnly면 최상위만.
    // id 오름차순으로 after 다음부터 최대 size개
    fun search(q: String?, parentId: Id?, rootsOnly: Boolean, after: Id?, size: Int): List<Organization>

    fun create(name: String, parentOrganizationId: Id?): Id

    fun update(id: Id, name: String, parentOrganizationId: Id?)

    // id 조직의 상위 조직 id를 가까운 것부터(자기 자신 제외). 순환 지정 확인용
    fun ancestorIds(id: Id): List<Id>

    // 조직별 상위 경로(최상위부터 바로 위 조직까지, 이름 포함). 최상위 조직은 빈 목록
    fun ancestors(ids: Collection<Id>): Map<Id, List<Pair<Id, String>>>

    // 조직별 하위 조직 수·프로그램 수(0이면 빠진다)
    fun childCounts(ids: Collection<Id>): Map<Id, Int>

    fun programCounts(ids: Collection<Id>): Map<Id, Int>
}
