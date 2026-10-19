package io.wid.dearby.application.collection

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectedActivityRef
import io.wid.dearby.domain.collection.CollectionFeedback
import io.wid.dearby.domain.collection.FeedbackKind
import io.wid.dearby.domain.collection.NewFeedback

// 수집 피드백(V43). 어드민 수정 경로가 남기고, 수집 에이전트가 찾아 쓴다
interface CollectionFeedbackRepository {

    fun record(feedback: NewFeedback): Id

    // 수집으로 생긴 활동이면 그 프로그램·이름·공식 안내 호스트. 직접 만든 활동이나 없는 활동은 null
    fun collectedActivity(activityId: Id): CollectedActivityRef?

    // 이 프로그램의 최근 피드백(지시문에 넣는다)
    fun latest(programId: Id, limit: Int): List<CollectionFeedback>

    // 원문과 비슷한 피드백(pg_trgm 기본 기준 0.3 이상). 같은 프로그램 → 같은 호스트 → 나머지 순, 같은 순위에서는 유사도 순
    fun search(programId: Id, hosts: Collection<String>, text: String, kind: FeedbackKind?, limit: Int): List<CollectionFeedback>

    // 이 프로그램에서 이름(공백·대소문자 무시)이 같은 피드백
    fun findByName(programId: Id, name: String, kinds: Collection<FeedbackKind>): List<CollectionFeedback>

    // 조심 목록 항목을 지우면 그 피드백도 지운다
    fun deleteByWatchEntry(watchEntryId: Id)
}
