package io.wid.dearby.application.collection

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectedActivity
import io.wid.dearby.domain.collection.DateLabelExample
import io.wid.dearby.domain.collection.DraftSaveResult
import io.wid.dearby.domain.collection.PreviousEdition
import java.util.*

// 저장소 포트. 구현은 adaptor/persistence
interface ActivityDraftRepository {

    // sourceId로 같은 활동을 찾는다. 없으면 초안으로 만들고, 아직 초안이면 내용을 갱신한다.
    // 게시된 활동은 덮어쓰지 않고, 관리자가 삭제한 출처는 다시 만들지 않는다.
    // 대상자 조건·대상 원문·참여 방식이 비어 있으면 같은 프로그램의 이전 기수에서 가져와 채우고 inherited_fields에 남긴다(admin-v1.md "이전 기수 정보 자동완성")
    fun saveDraft(programId: Id, sourceId: UUID, activity: CollectedActivity): DraftSaveResult

    // 같은 프로그램의 가장 최근 활동(게시 우선, 없으면 최신). 수집 지시문의 "참고(이전 기수)"로 넘긴다. 없으면 null
    fun findPreviousEdition(programId: Id): PreviousEdition?
    // 운영자가 직접 지정한(curated) 일정 중 원문 문구와 날짜가 모두 있는 것을 최신순으로 최대 limit개, 같은 문구는 하나만, 같은 프로그램 먼저(admin-v1.md "날짜 문구 피드백")
    fun findDateLabelExamples(programId: Id, limit: Int = 20): List<DateLabelExample>
}
