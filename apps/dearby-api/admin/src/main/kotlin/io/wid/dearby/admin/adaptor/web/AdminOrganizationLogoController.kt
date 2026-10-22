package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.AdminOrganizationLogoService
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

// 조직 로고(admin-v1.md "조직 로고"). ADMIN 역할만(AdminAccessRules). 응답은 프로그램 로고와 같은 {logoUrl}
@RestController
@RequestMapping("/v1/admin/organizations/{id}/logo")
class AdminOrganizationLogoController(private val logos: AdminOrganizationLogoService) {

    @PutMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun put(@PathVariable id: Long, @RequestPart("file") file: MultipartFile) = LogoUrlResponse(logos.put(id, file.contentType, file.bytes))

    @GetMapping
    fun get(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val logo = logos.get(id)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType)).body(logo.data)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = logos.delete(id)
}
