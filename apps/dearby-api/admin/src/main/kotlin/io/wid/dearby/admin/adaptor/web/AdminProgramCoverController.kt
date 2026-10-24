package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminProgramCoverService
import io.wid.dearby.domain.ProgramCoverInfo
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

// 프로그램 커버(admin-v1.md "프로그램 커버"). ADMIN 역할만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin/programs/{id}/cover")
class AdminProgramCoverController(private val covers: AdminProgramCoverService) {

    @PutMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun put(
        @PathVariable id: Long,
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) sourcePage: String?,
        @RequestParam(required = false) sourceImage: String?,
    ) = CoverResponse.from(covers.put(id, file.contentType, file.bytes, sourcePage, sourceImage))

    // {enabled}: 끄면 공개 GET이 404이고 발견·대기열의 coverUrl이 null이 된다
    @PatchMapping
    fun patch(@PathVariable id: Long, @RequestBody request: CoverEnabledRequest) = CoverResponse.from(covers.setEnabled(id, request.enabled))

    // 어드민 미리보기용(꺼진 커버도). 공개 이미지는 api 앱이 낸다
    @GetMapping
    fun get(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val cover = covers.get(id)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(cover.contentType)).body(cover.data)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = covers.delete(id)
}

data class CoverEnabledRequest(val enabled: Boolean)

// coverUrl은 공개 경로 `/v1/programs/{id}/cover?v=…`(꺼졌으면 공개 GET은 404)
data class CoverResponse(val coverUrl: String, val enabled: Boolean, val sourcePage: String?, val sourceImage: String?) {
    companion object {
        fun from(info: ProgramCoverInfo) = CoverResponse(info.url, info.enabled, info.sourcePage, info.sourceImage)
    }
}
