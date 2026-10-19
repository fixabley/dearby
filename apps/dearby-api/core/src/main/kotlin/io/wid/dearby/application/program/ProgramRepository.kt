package io.wid.dearby.application.program

import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Program

// 저장소 포트. 구현은 adaptor/persistence
interface ProgramRepository {

    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Program>

    // q는 한글·영문 이름에서 대소문자 없이 부분 일치. id 오름차순으로 after 다음부터 최대 size개
    fun search(q: String?, organizationId: Id?, after: Id?, size: Int): List<Program>

    fun create(organizationId: Id, name: String, nameEn: String?): Id

    // 같은 조직 아래에 띄어쓰기를 뺀 이름이 같은 프로그램이 있는지. english면 영문 이름끼리 비교. exceptId는 자기 자신
    fun existsName(organizationId: Id, name: String, exceptId: Id?, english: Boolean = false): Boolean

    fun update(id: Id, organizationId: Id, name: String, nameEn: String?, category: ActivityCategory?)

    fun activityCount(id: Id): Int

    // 프로그램과 그 연락처·수집 설정·수집 기록을 지운다. 활동 확인은 호출하는 쪽이 한다
    fun delete(id: Id): Boolean
}
