package io.wid.dearby.domain.profile

import io.wid.dearby.domain.Id
import java.time.OffsetDateTime

// 직무 활동 정보(2026-10-17): 조직처럼 상위 직무 아래 계층을 이룬다. 같은 계위에서 띄어쓰기를 뺀 이름이 유일
data class Job(val id: Id, val name: String, val nameEn: String?, val parentId: Id?, val createdAt: OffsetDateTime, val updatedAt: OffsetDateTime)
