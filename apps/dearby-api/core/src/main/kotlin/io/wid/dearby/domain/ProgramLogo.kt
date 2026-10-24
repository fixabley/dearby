package io.wid.dearby.domain

import java.time.OffsetDateTime

// 프로그램 로고 이미지(native-v1.md·admin-v1.md "프로그램 로고"). 인증자료처럼 DB(bytea)에 둔다
data class ProgramLogo(val programId: Id, val contentType: String, val data: ByteArray, val updatedAt: OffsetDateTime) {

    companion object {
        // 공개 URL(API origin 기준 경로). v는 바꿀 때마다 달라져 immutable 캐시를 깨뜨린다
        fun url(programId: Id, updatedAt: OffsetDateTime): String = "/v1/programs/$programId/logo?v=${updatedAt.toEpochSecond()}"
    }
}

// 조직 로고(admin-v1.md "조직 로고"). 프로그램 로고와 같은 규칙
data class OrganizationLogo(val organizationId: Id, val contentType: String, val data: ByteArray, val updatedAt: OffsetDateTime) {

    companion object {
        fun url(organizationId: Id, updatedAt: OffsetDateTime): String = "/v1/organizations/$organizationId/logo?v=${updatedAt.toEpochSecond()}"
    }
}

// 올릴 수 있는 로고 파일: PNG·JPEG·WebP 1MB 이하. 맞으면 정규화한 content type을 돌려준다
object LogoFile {
    private val TYPES = setOf("image/png", "image/jpeg", "image/webp")
    private const val MAX_BYTES = 1024 * 1024

    fun contentType(contentType: String?, data: ByteArray): String {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type == null || type !in TYPES || data.isEmpty() || data.size > MAX_BYTES) throw InvalidInputException("로고는 PNG·JPEG·WebP 1MB 이하여야 합니다")
        return type
    }
}

// 프로그램에 실제로 보일 로고(native-v1.md "조직 로고와 로고 계승"): 프로그램 로고, 없으면 가장 가까운 조직 로고
data class EffectiveLogo(val url: String, val source: LogoSource, val organizationId: Id?, val organizationName: String?, val brandColor: String? = null)

enum class LogoSource { PROGRAM, ORGANIZATION }

// 프로그램 커버(native-v1.md "맞춤 대기열" 커버 이미지, admin-v1.md "프로그램 커버"). 로고처럼 DB에 두고 enabled=false면 공개하지 않는다
data class ProgramCover(val info: ProgramCoverInfo, val contentType: String, val data: ByteArray)

// 이미지 바이트 없는 정보(목록·어드민 표시용)
data class ProgramCoverInfo(val programId: Id, val sourcePage: String?, val sourceImage: String?, val enabled: Boolean, val updatedAt: OffsetDateTime) {
    val url: String get() = "/v1/programs/$programId/cover?v=${updatedAt.toEpochSecond()}"
}

// 올릴 수 있는 커버: 로고와 같은 형식 검사, 350KB 이하. 세로 4:5·1080×1350은 권장(서버가 픽셀을 읽지 않는다)
object CoverFile {
    private val TYPES = setOf("image/png", "image/jpeg", "image/webp")
    private const val MAX_BYTES = 350 * 1024

    fun contentType(contentType: String?, data: ByteArray): String {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type == null || type !in TYPES || data.isEmpty() || data.size > MAX_BYTES) throw InvalidInputException("커버는 PNG·JPEG·WebP 350KB 이하여야 합니다")
        return type
    }
}
