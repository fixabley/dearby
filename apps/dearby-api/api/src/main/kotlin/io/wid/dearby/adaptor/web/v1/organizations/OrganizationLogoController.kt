package io.wid.dearby.adaptor.web.v1.organizations

import io.wid.dearby.application.organization.OrganizationLogoRepository
import io.wid.dearby.domain.NotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 조직 로고 공개 이미지(native-v1.md "조직 로고와 로고 계승"). 프로그램 로고와 같은 영구 캐시 규칙, 인증 없음(ApiAccessRules)
@RestController
@RequestMapping("/v1/organizations/{id}/logo")
class OrganizationLogoController(private val logos: OrganizationLogoRepository) {

    @GetMapping
    fun logo(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val logo = logos.find(id) ?: throw NotFoundException("로고가 없습니다", "Organization logo not found: $id")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(logo.contentType))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .body(logo.data)
    }
}
