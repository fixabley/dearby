package io.wid.dearby.application.program

import java.time.OffsetDateTime

// 저장소 포트. 구현은 adaptor/persistence.
// "열린 활동" 규칙은 OpenActivityConditions(native-v1.md "열린 활동 판정 일원화"): 시각·날짜 모집 기간이 지금을 포함하거나, 모집 기간이 없으면 활동 시작이 아직 오지 않은 게시 활동
interface ProgramSummaryRepository {

    // 열린 프로그램을 가장 이른 모집 마감 오름차순으로(마감 없는 프로그램은 뒤), openOnly가 꺼지면 그 뒤에 최근 2년 안 활동이 있던 프로그램을 최근 활동 시작 순으로. cursor부터 최대 size개
    fun search(condition: ProgramSummarySearch, now: OffsetDateTime): List<ProgramSummary>

    // 대상자 필터가 있을 때: 열린 활동(위 규칙 + 유형 + 대상자 1차 판정)을 모두 돌려준다. 묶음은 서비스가 2차 판정 뒤 만든다
    fun openActivities(condition: ProgramSummarySearch, now: OffsetDateTime): List<OpenActivity>
}
