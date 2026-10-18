package io.wid.dearby.domain.collection

import io.wid.dearby.domain.Id
import java.time.OffsetDateTime
import java.util.*

// 조심 목록 종류(admin-v1.md "조심 목록"). ALIAS: 정본 활동의 다른 표기 → 정본 초안을 갱신. EXCLUDE: 이 프로그램의 활동이 아닌 이름 → 저장하지 않음
enum class WatchKind { ALIAS, EXCLUDE }

// 운영자가 지정한, 수집기가 피할 이름 하나
data class WatchEntry(
    val id: Id,
    val programId: Id,
    val name: String,
    val normalizedName: String, // 소문자·공백 제거. 추출된 이름과 대조하는 키
    val kind: WatchKind,
    val canonicalActivityId: Id?, // ALIAS의 정본 활동. 정본이 지워지면 null
    val canonicalActivityName: String?,
    val canonicalSourceId: UUID?, // 정본의 수집 출처. 없으면(직접 만든 활동) 초안 갱신 대상이 아니다
    val reason: String?,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun normalize(name: String): String = name.lowercase().replace(Regex("\\s+"), "")
    }
}
