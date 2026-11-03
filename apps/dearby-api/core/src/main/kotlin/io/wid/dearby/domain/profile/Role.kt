package io.wid.dearby.domain.profile

import io.wid.dearby.domain.Id
import java.time.OffsetDateTime

// 역할이 귀속되는 단위(native-v1.md "이력 (History) — 역할 매칭")
enum class RoleOwnerKind { ORGANIZATION, PROGRAM, ACTIVITY }

// COLLECTED는 수집이 공고의 모집 역할 문구로 만든 활동 역할. 운영자가 고치면 CURATED가 되어 다음 수집이 바꾸지 않는다
enum class RoleSource { CURATED, COLLECTED }

// 귀속 단위. id가 없으면 사용자가 이력에 이름만 직접 적은 단위(승인 대기, 역할 행에는 늘 id가 있다)
data class RoleOwner(val kind: RoleOwnerKind, val id: Id?, val name: String)

// 조직·프로그램·활동 중 하나에 귀속된 역할. 이름은 그 단위 안에서 띄어쓰기를 뺀 값으로 유일
data class Role(
    val id: Id,
    val name: String,
    val nameEn: String?,
    val owner: RoleOwner,
    val source: RoleSource,
    val position: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
