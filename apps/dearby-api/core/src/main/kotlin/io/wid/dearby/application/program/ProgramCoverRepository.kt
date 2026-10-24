package io.wid.dearby.application.program

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgramCover
import io.wid.dearby.domain.ProgramCoverInfo

// 프로그램 커버 저장소 포트. 구현은 adaptor/persistence
interface ProgramCoverRepository {

    // 꺼진 커버도 돌려준다(공개 여부는 부르는 쪽이 enabled로 본다)
    fun find(programId: Id): ProgramCover?

    // 커버가 있는 프로그램만(꺼진 것 포함)
    fun infoByProgram(programIds: Collection<Id>): Map<Id, ProgramCoverInfo>

    // 있으면 바꾼다(켜진 상태로)
    fun save(cover: ProgramCover)

    // 커버가 없으면 false. updatedAt도 바꿔 공개 주소(v)를 새로 한다
    fun setEnabled(programId: Id, enabled: Boolean): Boolean

    fun delete(programId: Id)
}
