package io.wid.dearby.domain

// 일정 상태(native-v1.md "내 활동 서버 저장·일정 상태"). iCal STATUS인 ScheduleStatus와 다른 것으로, 사용자가 단계마다 고르는 진행 상태
enum class ProgressKind { WAITING, PASSED, FAILED, OTHER }

// id가 null이면 어드민이 지정하지 않은 기본 상태(저장하지 않음)
data class ProgressStatus(val id: Id?, val name: String, val kind: ProgressKind) {
    companion object {
        // 일정에 어드민 상태가 없을 때 쓰는 기본 세 개
        val DEFAULTS = listOf(
            ProgressStatus(null, "대기", ProgressKind.WAITING),
            ProgressStatus(null, "통과", ProgressKind.PASSED),
            ProgressStatus(null, "탈락", ProgressKind.FAILED),
        )
    }
}
