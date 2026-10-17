package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.Job

// 직무 활동 정보 저장소(조직과 같은 계층 규칙). 구현은 adaptor/persistence
interface JobRepository {

    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Job>

    // q는 한글·영문 이름에서 대소문자 없이 부분 일치. parentId가 있으면 그 아래만, rootsOnly면 최상위만. id 오름차순으로 after 다음부터 최대 size개
    fun search(q: String?, parentId: Id?, rootsOnly: Boolean, after: Id?, size: Int): List<Job>

    fun create(name: String, nameEn: String?, parentId: Id?): Id

    fun update(id: Id, name: String, nameEn: String?, parentId: Id?)

    // 같은 상위 아래(null이면 최상위끼리)에 띄어쓰기를 뺀 이름이 같은 직무가 있는지. english면 영문 이름끼리. exceptId는 자기 자신
    fun existsSiblingName(parentId: Id?, name: String, exceptId: Id?, english: Boolean = false): Boolean

    fun delete(id: Id): Boolean

    // id 직무의 상위 id를 가까운 것부터(자기 자신 제외). 순환 지정 확인용
    fun ancestorIds(id: Id): List<Id>

    // 직무별 상위 경로(최상위부터 바로 위까지, 이름 포함). 최상위는 빈 목록
    fun ancestors(ids: Collection<Id>): Map<Id, List<Pair<Id, String>>>

    // 직무별 하위 직무 수(0이면 빠진다)
    fun childCounts(ids: Collection<Id>): Map<Id, Int>

    // 프로필이 쓰고 있는지
    fun inUse(id: Id): Boolean
}
