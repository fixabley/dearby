package io.wid.dearby.domain.discovery

import io.wid.dearby.domain.Id
import java.time.OffsetDateTime

// 맞춤 대기열 신호(native-v1.md "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터"). REMOVE는 서버가 남긴다(내 활동에서 지움)
enum class SignalKind(val weight: Double, val fromClient: Boolean = true) {
    LIKE(0.4), DISMISS(-0.3), LESS_FIELD(0.0), HIDE_PROGRAM(0.0), INELIGIBLE(0.0),
    COMPLETE(0.1), SKIP_EARLY(-0.05), OPEN_DETAIL(0.1), REMOVE(-0.2, fromClient = false);

    // 대기열 "본 것"(seen)으로 세는 반응
    val reacted: Boolean get() = this in REACTIONS

    companion object {
        private val REACTIONS = setOf(LIKE, DISMISS, HIDE_PROGRAM, INELIGIBLE, LESS_FIELD, COMPLETE, SKIP_EARLY)
    }
}

data class Signal(val activityId: Id, val kind: SignalKind, val tagId: Id?, val at: OffsetDateTime)

// 내 활동(user_activities)과 일정 상태에서 읽은 신호. 활동마다 가장 강한 것 하나: 참여 1.0 > 지원 0.7 > 내 활동에 추가 0.5
data class ImplicitSignal(val activityId: Id, val programId: Id, val strength: Engagement, val at: OffsetDateTime)

enum class Engagement(val weight: Double) { PARTICIPATED(1.0), APPLIED(0.7), ADDED(0.5) }

// 신호가 걸린 활동의 소속(닫힌 활동도). 조직 친화도·새 기수 판단에 쓴다
data class ActivityRef(val activityId: Id, val programId: Id, val organizationId: Id, val name: String)

// 대기열 한 칸(저장된 모양)
data class QueueRow(val position: Int, val activityId: Id, val reasons: List<String>, val generatedAt: OffsetDateTime)
