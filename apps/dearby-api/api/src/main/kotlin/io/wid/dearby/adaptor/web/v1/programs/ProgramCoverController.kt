package io.wid.dearby.adaptor.web.v1.programs

import io.wid.dearby.application.program.ProgramCoverRepository
import io.wid.dearby.domain.NotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 프로그램 커버 공개 이미지(native-v1.md "맞춤 대기열"). 로고와 같은 캐시 규칙이고, 없거나 꺼졌으면 404(주최 측 요청 시 바로 내린다: 끄면 v가 바뀐다)
@RestController
@RequestMapping("/v1/programs/{id}/cover")
class ProgramCoverController(private val covers: ProgramCoverRepository) {

    @GetMapping
    fun cover(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val cover = covers.find(id)?.takeIf { it.info.enabled } ?: throw NotFoundException("커버가 없습니다", "Program cover not found: $id")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(cover.contentType))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .body(cover.data)
    }
}
