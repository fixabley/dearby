package io.wid.dearby.application.collection

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.curation.CollectionSettings
import java.time.LocalDate

// 저장소 포트. 구현은 adaptor/persistence
interface CollectionSettingsRepository {

    // 설정 행이 있는 프로그램만 들어 있다
    fun findAllByProgramId(programIds: Collection<Id>): Map<Id, CollectionSettings>

    fun save(programId: Id, settings: CollectionSettings)

    // 프로그램별 마지막 성공 수집 한국 날짜. 성공한 적이 없으면 빠진다
    fun lastSucceededOn(programIds: Collection<Id>): Map<Id, LocalDate>
}
