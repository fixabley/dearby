package io.wid.dearby.application.collection

import io.wid.dearby.domain.collection.CollectionTarget
import java.time.LocalDate

// 저장소 포트. 구현은 adaptor/persistence
interface CollectionTargetRepository {

    // 수집이 켜져 있고, 마지막 성공 수집 날짜부터 주기(interval_days)가 지난 프로그램
    fun findDue(today: LocalDate): List<CollectionTarget>
}
