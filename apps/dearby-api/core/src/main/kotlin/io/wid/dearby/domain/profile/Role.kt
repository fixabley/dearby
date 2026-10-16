package io.wid.dearby.domain.profile

import io.wid.dearby.domain.Id
import java.time.OffsetDateTime

// 역할 카탈로그(이력의 역할). 이름은 같은 계위 규칙처럼 띄어쓰기를 뺀 값으로 유일
data class Role(val id: Id, val name: String, val nameEn: String?, val createdAt: OffsetDateTime, val updatedAt: OffsetDateTime)
