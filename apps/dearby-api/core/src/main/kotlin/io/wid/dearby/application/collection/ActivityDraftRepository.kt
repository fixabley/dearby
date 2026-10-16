package io.wid.dearby.application.collection

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectedActivity
import io.wid.dearby.domain.collection.DraftSaveResult
import java.util.*

// 저장소 포트. 구현은 adaptor/persistence
interface ActivityDraftRepository {

    // sourceId로 같은 활동을 찾는다. 없으면 초안으로 만들고, 아직 초안이면 내용을 갱신한다.
    // 게시된 활동은 덮어쓰지 않고, 관리자가 삭제한 출처는 다시 만들지 않는다
    fun saveDraft(programId: Id, sourceId: UUID, activity: CollectedActivity): DraftSaveResult
}
