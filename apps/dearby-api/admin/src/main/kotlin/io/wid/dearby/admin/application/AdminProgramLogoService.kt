package io.wid.dearby.admin.application

import io.wid.dearby.application.program.ProgramLogoRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.LogoFile
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.ProgramLogo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 프로그램 로고 올리기·보기·지우기(admin-v1.md "프로그램 로고"). 공개 이미지는 api 앱의 GET /v1/programs/{id}/logo
@Service
class AdminProgramLogoService(private val programs: ProgramRepository, private val logos: ProgramLogoRepository) {

    // PNG·JPEG·WebP 1MB 이하만. 있으면 바꾼다. 돌려주는 값은 공개 경로(logoUrl)
    @Transactional
    fun put(programId: Id, contentType: String?, data: ByteArray): String {
        if (programs.findAllById(listOf(programId)).isEmpty()) throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $programId")
        val type = LogoFile.contentType(contentType, data)
        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        logos.save(ProgramLogo(programId, type, data, updatedAt))
        return ProgramLogo.url(programId, updatedAt)
    }

    @Transactional(readOnly = true)
    fun get(programId: Id): ProgramLogo =
        logos.find(programId) ?: throw NotFoundException("로고가 없습니다", "Program logo not found: $programId")

    // 없어도 그대로 끝난다(204)
    @Transactional
    fun delete(programId: Id) = logos.delete(programId)
}
