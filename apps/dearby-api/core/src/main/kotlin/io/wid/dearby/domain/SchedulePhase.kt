package io.wid.dearby.domain

// 일정이 선발 과정인지 활동 일정인지(native-v1.md "선발 과정·활동 일정 분리"). 통과·대기·탈락은 SELECTION에만 붙는다
enum class SchedulePhase {
    SELECTION, // 서류·코딩테스트·면접·발표·합격자 발표까지
    ACTIVITY; // OT·세션·교육·수료 등 붙은 뒤의 활동

    companion object {
        // 저장된 값이 없을 때의 판정(V62 채우기와 같은 규칙). 모집 슬롯은 늘 선발, 활동 기간 슬롯은 늘 활동이라 저장값보다 앞선다
        fun resolve(stored: SchedulePhase?, slot: String?, kind: ScheduleKind, hasAdminStatuses: Boolean): SchedulePhase = when (slot) {
            "APPLICATION" -> SELECTION
            "ACTIVITY" -> ACTIVITY
            else -> stored ?: if (kind != ScheduleKind.EVENT || hasAdminStatuses) SELECTION else ACTIVITY
        }
    }
}
