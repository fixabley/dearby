package io.wid.dearby.application.collection

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.domain.collection.WatchKind

// 조심 목록 저장소 포트. 구현은 adaptor/persistence
interface WatchlistRepository {

    fun findAllByProgramId(programId: Id): List<WatchEntry>

    fun findById(id: Id): WatchEntry?

    // 같은 프로그램에 같은 normalizedName이 있으면 DuplicateKeyException
    fun create(programId: Id, name: String, kind: WatchKind, canonicalActivityId: Id?, reason: String?): WatchEntry

    fun delete(id: Id): Boolean
}
