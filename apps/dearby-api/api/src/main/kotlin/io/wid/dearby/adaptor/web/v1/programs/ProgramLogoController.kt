package io.wid.dearby.adaptor.web.v1.programs

import io.wid.dearby.application.program.ProgramLogoRepository
import io.wid.dearby.domain.NotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 프로그램 로고 공개 이미지(native-v1.md "프로그램 로고"). 인증 없이 받는다(ApiAccessRules).
// URL의 v(updated_at epoch 초)가 바뀔 때마다 새 주소가 되므로 영구 캐시로 낸다
@RestController
@RequestMapping("/v1/programs/{id}/logo")
class ProgramLogoController(private val logos: ProgramLogoRepository) {

    @GetMapping
    fun logo(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val logo = logos.find(id) ?: throw NotFoundException("로고가 없습니다", "Program logo not found: $id")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(logo.contentType))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .body(logo.data)
    }
}
