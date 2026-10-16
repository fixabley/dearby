package io.wid.dearby.application.collection

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectionReport
import java.time.LocalDate

// 저장소 포트. 구현은 adaptor/persistence. 프로그램 하나 × 한국 날짜 하루에 한 번만 시작한다
interface CollectionRunRepository {

    // 시작을 기록하고 id를 돌려준다. 같은 프로그램·날짜의 기록이 이미 있으면 null
    fun start(programId: Id, runDate: LocalDate, officialOnly: Boolean): Id?

    fun finish(runId: Id, report: CollectionReport)
}
