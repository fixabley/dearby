package io.wid.dearby.application.collection

import io.wid.dearby.domain.CollectedActivity
import io.wid.dearby.domain.Id
import java.util.*

// 저장소 포트. 구현은 adaptor/persistence
interface ActivityDraftRepository {

    // sourceId로 같은 활동을 찾는다. 없으면 초안으로 만들고, 아직 초안이면 내용을 갱신한다.
    // 게시된 활동은 관리자 검토를 거쳤으므로 덮어쓰지 않고 false를 돌려준다
    fun saveDraft(programId: Id, sourceId: UUID, activity: CollectedActivity): Boolean
}
