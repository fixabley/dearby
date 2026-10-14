package io.wid.dearby.application.program

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Program

// 저장소 포트. 구현은 adaptor/persistence
interface ProgramRepository {

    // 없는 id는 결과에서 빠진다
    fun findAllById(ids: Collection<Id>): List<Program>
}
