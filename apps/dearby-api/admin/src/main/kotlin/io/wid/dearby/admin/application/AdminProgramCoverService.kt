package io.wid.dearby.admin.application

import io.wid.dearby.application.program.ProgramCoverRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.CoverFile
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.ProgramCover
import io.wid.dearby.domain.ProgramCoverInfo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 프로그램 커버 올리기·끄기·보기·지우기(admin-v1.md "프로그램 커버"). 공개 이미지는 api 앱의 GET /v1/programs/{id}/cover
@Service
class AdminProgramCoverService(private val programs: ProgramRepository, private val covers: ProgramCoverRepository) {

    // PNG·JPEG·WebP 350KB 이하. 있으면 바꾸고 켠다. 출처 주소는 https(선택)
    @Transactional
    fun put(programId: Id, contentType: String?, data: ByteArray, sourcePage: String?, sourceImage: String?): ProgramCoverInfo {
        if (programs.findAllById(listOf(programId)).isEmpty()) throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $programId")
        val type = CoverFile.contentType(contentType, data)
        val info = ProgramCoverInfo(programId, source(sourcePage), source(sourceImage), enabled = true, updatedAt = OffsetDateTime.now(ZoneOffset.UTC))
        covers.save(ProgramCover(info, type, data))
        return info
    }

    // 주최 측 요청이면 바로 끈다(공개 404, coverUrl null). 없으면 404
    @Transactional
    fun setEnabled(programId: Id, enabled: Boolean): ProgramCoverInfo {
        if (!covers.setEnabled(programId, enabled)) throw notFound(programId)
        return covers.infoByProgram(listOf(programId)).getValue(programId)
    }

    @Transactional(readOnly = true)
    fun get(programId: Id): ProgramCover = covers.find(programId) ?: throw notFound(programId)

    @Transactional
    fun delete(programId: Id) = covers.delete(programId)

    private fun notFound(programId: Id) = NotFoundException("커버가 없습니다", "Program cover not found: $programId")

    private fun source(value: String?): String? = value?.trim()?.ifEmpty { null }?.also {
        if (!it.startsWith("https://") || it.length > MAX_URL) throw InvalidInputException("출처 주소는 ${MAX_URL}자 이하 https 주소여야 합니다")
    }

    private companion object {
        const val MAX_URL = 2000
    }
}
