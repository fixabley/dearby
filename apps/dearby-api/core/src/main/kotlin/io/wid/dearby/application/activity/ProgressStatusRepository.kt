package io.wid.dearby.application.activity

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgressStatus

// 일정 상태 저장소 포트(native-v1.md "내 활동 서버 저장·일정 상태"). 구현은 adaptor/persistence
interface ProgressStatusRepository {

    // 일정의 상태 목록을 통째로 바꾼다(순서 = 목록 순서). id가 있는 항목은 그 행을 고치고, 없으면 새로 넣고, 빠진 행은 지운다.
    // 지운 상태를 고른 사용자 선택은 그 상태의 기본 종류로 옮기고(OTHER는 기본 종류가 없어 선택을 지운다), 수집 일정이면 운영자 지정(curated)으로 남긴다
    fun replace(scheduleId: Id, statuses: List<ProgressStatus>)
}
