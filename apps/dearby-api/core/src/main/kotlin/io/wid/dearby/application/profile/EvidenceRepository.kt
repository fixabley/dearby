package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Evidence
import java.util.*

// 인증자료 파일 저장소. 구현은 adaptor/persistence
interface EvidenceRepository {

    fun add(historyId: UUID, userId: UserId, evidence: Evidence)

    fun find(historyId: UUID, evidenceId: UUID): Evidence?

    fun delete(historyId: UUID, evidenceId: UUID): Boolean

    fun count(historyId: UUID): Int
}
