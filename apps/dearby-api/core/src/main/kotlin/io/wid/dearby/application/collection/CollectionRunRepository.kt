package io.wid.dearby.application.collection

import io.wid.dearby.domain.CollectionReport
import io.wid.dearby.domain.Id
import java.time.LocalDate

// 저장소 포트. 구현은 adaptor/persistence. 설정 하나 × 한국 날짜 하루에 한 번만 시작한다
interface CollectionRunRepository {

    // 시작을 기록하고 id를 돌려준다. 같은 설정·날짜의 기록이 이미 있으면 null
    fun start(programSourceId: Id, runDate: LocalDate): Id?

    fun finish(runId: Id, report: CollectionReport)
}
