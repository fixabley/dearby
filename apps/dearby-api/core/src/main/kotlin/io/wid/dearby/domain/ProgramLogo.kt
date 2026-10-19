package io.wid.dearby.domain

import java.time.OffsetDateTime

// 프로그램 로고 이미지(native-v1.md·admin-v1.md "프로그램 로고"). 인증자료처럼 DB(bytea)에 둔다
data class ProgramLogo(val programId: Id, val contentType: String, val data: ByteArray, val updatedAt: OffsetDateTime) {

    companion object {
        // 공개 URL(API origin 기준 경로). v는 바꿀 때마다 달라져 immutable 캐시를 깨뜨린다
        fun url(programId: Id, updatedAt: OffsetDateTime): String = "/v1/programs/$programId/logo?v=${updatedAt.toEpochSecond()}"
    }
}
