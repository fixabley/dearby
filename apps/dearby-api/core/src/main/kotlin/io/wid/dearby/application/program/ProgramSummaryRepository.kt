package io.wid.dearby.application.program

import java.time.OffsetDateTime

// 저장소 포트. 구현은 adaptor/persistence.
// "열린 활동" 규칙은 OpenActivityConditions(native-v1.md "열린 활동 판정 일원화"): 시각·날짜 모집 기간이 지금을 포함하거나, 모집 기간이 없으면 활동 시작이 아직 오지 않은 게시 활동
interface ProgramSummaryRepository {

    // 열린 프로그램을 가장 이른 모집 마감 오름차순으로(마감 없는 프로그램은 뒤), openOnly가 꺼지면 그 뒤에 최근 3년 안 활동이 있던 프로그램을 최근 활동 시작 순으로. cursor부터 최대 size개
    fun search(condition: ProgramSummarySearch, now: OffsetDateTime): List<ProgramSummary>

    // 대상자 필터가 있을 때: 열린 활동(위 규칙 + 유형 + 대상자 1차 판정)을 모두 돌려준다. 묶음은 서비스가 2차 판정 뒤 만든다
    fun openActivities(condition: ProgramSummarySearch, now: OffsetDateTime): List<OpenActivity>

    // 묶음 머리의 태그(native-v1.md "태그", "분야·성격 태그와 아이콘"). 규칙은 ProgramTags. 모든 programIds에 항목이 있다
    fun tagsByProgram(programIds: Collection<io.wid.dearby.domain.Id>, now: OffsetDateTime): Map<io.wid.dearby.domain.Id, ProgramTags>
}

// tags: 프로그램 태그가 있으면 그것, 없으면 최근 3년 게시 활동 태그의 합집합(최대 6개), 사전 순(이전 앱 호환)
// fieldTags: 분야 태그 전부, 서버 순서(활동 연관도 평균 → position → 사전 순, 하위 분야가 있는 상위 분야 뺌). traitTags: 성격 태그 전부, 사전 순
data class ProgramTags(val tags: List<io.wid.dearby.domain.Tag>, val fieldTags: List<io.wid.dearby.domain.Tag>, val traitTags: List<io.wid.dearby.domain.Tag>)
