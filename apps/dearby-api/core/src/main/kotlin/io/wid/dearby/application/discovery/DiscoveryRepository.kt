package io.wid.dearby.application.discovery

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.discovery.ActivityRef
import io.wid.dearby.domain.discovery.ImplicitSignal
import io.wid.dearby.domain.discovery.QueueRow
import io.wid.dearby.domain.discovery.Signal
import io.wid.dearby.domain.discovery.SignalKind
import java.time.LocalDate
import java.time.OffsetDateTime

// 맞춤 대기열 저장소 포트(신호·벡터 읽기·친화도·취향·대기열). 구현은 adaptor/persistence. 사용자별 표는 users cascade라 계정 삭제와 함께 지워진다
interface DiscoveryRepository {

    // (사용자, 활동, 종류)마다 한 행: 있으면 시각·tagId만 새로 한다
    fun saveSignal(userId: UserId, activityId: Id, kind: SignalKind, tagId: Id?, at: OffsetDateTime)
    fun deleteSignal(userId: UserId, activityId: Id, kind: SignalKind)
    fun signals(userId: UserId): List<Signal>

    // 내 활동과 일정 상태(참여·지원·추가)
    fun implicitSignals(userId: UserId): List<ImplicitSignal>

    // 지운 활동은 빠진다(게시 여부와 상관없이)
    fun activityRefs(activityIds: Collection<Id>): Map<Id, ActivityRef>

    // 밤 배치가 대기열을 만들 사용자: 추천 설정·신호·내 활동·대기열 중 하나라도 있는 사용자
    fun queueUsers(): List<UserId>

    // 벡터는 지금 읽는 모델(embedding_settings)의 것만. 모델이 없으면 null
    fun activeModel(): String?
    fun activityVectors(activityIds: Collection<Id>, model: String): Map<Id, FloatArray>
    fun tagVectors(tagIds: Collection<Id>, model: String): Map<Id, FloatArray>

    // null이면 지운다
    fun saveTaste(userId: UserId, model: String?, vector: FloatArray?)

    // 통째로 바꾼다(0보다 큰 것만)
    fun saveAffinity(userId: UserId, scores: Map<Id, Double>)

    fun queue(userId: UserId, date: LocalDate): List<QueueRow>

    // 그 날짜의 대기열을 rows로 바꾸고 지난 날짜는 지운다
    fun replaceQueue(userId: UserId, date: LocalDate, rows: List<QueueRow>)
}
