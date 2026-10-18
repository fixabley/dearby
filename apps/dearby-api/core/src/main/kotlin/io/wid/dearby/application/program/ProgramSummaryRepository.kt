package io.wid.dearby.application.program

import java.time.OffsetDateTime

// 저장소 포트. 구현은 adaptor/persistence.
// "열린 활동"은 게시된 활동 중 모집 기간(APPLICATION 일정, 시각 일정)으로 보아 지금 모집 중인 것: 시작이 없거나 지났고, 끝이 없거나 아직 오지 않았다.
// 모집 기간이 없거나 날짜만 있는 활동은 열린 활동이 아니다(클라이언트의 "모집 여부 확인 필요"와 같다)
interface ProgramSummaryRepository {

    // 가장 이른 모집 마감 오름차순(마감 없는 프로그램은 뒤), 같으면 이름·id 순. cursor부터 최대 size개
    fun search(condition: ProgramSummarySearch, now: OffsetDateTime): List<ProgramSummary>
}
