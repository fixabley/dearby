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

    // 대표색(#RRGGBB)이 있는 것만(native-v1.md "맞춤 대기열" brandColor). 저장할 때 서버가 계산한다
    fun brandColors(ids: Collection<Id>): Map<Id, String>

    // 대표색이 비어 있는 로고를 다시 계산한다(밤 배치). 색을 찾은 수
    fun backfillBrandColors(): Int
}
