package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminProgramLogoService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

// 프로그램 로고(admin-v1.md "프로그램 로고"). ADMIN 역할만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin/programs/{id}/logo")
class AdminProgramLogoController(private val logos: AdminProgramLogoService) {

    @PutMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun put(@PathVariable id: Long, @RequestPart("file") file: MultipartFile) = LogoUrlResponse(logos.put(id, file.contentType, file.bytes))

    // 어드민 미리보기용. 공개 이미지는 api 앱이 낸다
    @GetMapping
    fun get(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val logo = logos.get(id)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType)).body(logo.data)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = logos.delete(id)
}

// PUT 응답(프로그램·조직 로고 공통): 공개 경로 `/v1/programs/{id}/logo?v=…` 또는 `/v1/organizations/{id}/logo?v=…`
data class LogoUrlResponse(val logoUrl: String)
