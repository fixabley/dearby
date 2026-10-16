package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.application.profile.ProfileService
import io.wid.dearby.domain.InvalidInputException
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

// native-v1.md "인증자료". 이력 주인만. 파일은 DB에 둔다
@RestController
@RequestMapping("/v1/profile/histories/{historyId}/evidence")
class EvidenceController(private val profiles: ProfileService) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(@AuthenticationPrincipal jwt: Jwt, @PathVariable historyId: UUID, @RequestPart("file") file: MultipartFile): EvidenceResponse {
        val contentType = file.contentType ?: throw InvalidInputException("파일 종류를 알 수 없습니다")
        return EvidenceResponse.from(profiles.addEvidence(userId(jwt), historyId, file.originalFilename ?: "", contentType, file.bytes))
    }

    @GetMapping("/{evidenceId}")
    fun download(@AuthenticationPrincipal jwt: Jwt, @PathVariable historyId: UUID, @PathVariable evidenceId: UUID): ResponseEntity<ByteArray> {
        val evidence = profiles.evidence(userId(jwt), historyId, evidenceId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(evidence.meta.contentType))
            .header("Content-Disposition", ContentDisposition.inline().filename(evidence.meta.fileName, Charsets.UTF_8).build().toString())
            .body(evidence.data)
    }

    @DeleteMapping("/{evidenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable historyId: UUID, @PathVariable evidenceId: UUID) =
        profiles.deleteEvidence(userId(jwt), historyId, evidenceId)

    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
