package io.wid.dearby.application.program

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgramLogo
import java.time.OffsetDateTime

// 프로그램 로고 저장소 포트. 구현은 adaptor/persistence
interface ProgramLogoRepository {

    fun find(programId: Id): ProgramLogo?

    // 로고가 있는 프로그램만 돌려준다(목록의 logoUrl용)
    fun updatedAtByProgram(programIds: Collection<Id>): Map<Id, OffsetDateTime>

    // 있으면 바꾼다
    fun save(logo: ProgramLogo)

    // 없어도 그냥 끝난다
    fun delete(programId: Id)
}
