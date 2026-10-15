package io.wid.dearby.application.program

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Program

// 저장소 포트. 구현은 adaptor/persistence
interface ProgramRepository {

    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Program>

    // q는 이름에서 대소문자 없이 부분 일치. id 오름차순으로 after 다음부터 최대 size개
    fun search(q: String?, organizationId: Id?, after: Id?, size: Int): List<Program>

    fun create(organizationId: Id, name: String): Id

    fun update(id: Id, organizationId: Id, name: String)
}
