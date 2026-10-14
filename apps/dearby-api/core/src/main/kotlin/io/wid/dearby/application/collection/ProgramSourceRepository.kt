package io.wid.dearby.application.collection

import io.wid.dearby.domain.ProgramSource

// 저장소 포트. 구현은 adaptor/persistence
interface ProgramSourceRepository {

    fun findEnabled(): List<ProgramSource>
}
