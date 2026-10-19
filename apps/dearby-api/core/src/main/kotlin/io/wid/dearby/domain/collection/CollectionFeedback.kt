package io.wid.dearby.domain.collection

import io.wid.dearby.domain.Id
import java.time.OffsetDateTime

// 수집 피드백 종류(collection_feedback.kind)
enum class FeedbackKind { DATE_CURATED, PERIOD_SWAP, FIELD_CURATED, DRAFT_DISMISSED, WATCH_ALIAS, WATCH_EXCLUDE }

// 운영자가 어드민에서 바로잡은 것 하나. sourceText는 수집기가 본 원문(날짜 문구·이름·수집 값), correctedValue는 운영자가 정한 값(JSON 문자열)
data class CollectionFeedback(
    val id: Id,
    val kind: FeedbackKind,
    val programId: Id?,
    val programName: String?,
    val host: String?,
    val sourceText: String,
    val correctedValue: String,
    val reason: String?,
    val activityId: Id?,
    val createdAt: OffsetDateTime,
    val similarity: Double? = null, // 검색에서만. 0~1 글자 세 개 단위 유사도
)

// 새로 남길 피드백. correctedValue는 JSON으로 저장한다
data class NewFeedback(
    val kind: FeedbackKind,
    val programId: Id?,
    val host: String?,
    val sourceText: String,
    val correctedValue: Map<String, Any?>,
    val reason: String? = null,
    val activityId: Id? = null,
)

// 수집으로 생긴 활동(source_id 있음)의 피드백 기준. 운영자가 직접 만든 활동은 수집기 피드백이 아니라 없다
data class CollectedActivityRef(val activityId: Id, val programId: Id, val name: String, val host: String?)
