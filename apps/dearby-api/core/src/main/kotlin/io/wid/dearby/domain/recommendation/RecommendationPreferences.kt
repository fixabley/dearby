package io.wid.dearby.domain.recommendation

import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Id
import java.time.OffsetDateTime

// 추천 설정(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"): 퍼널의 관심 분야·활동 종류·얻고 싶은 것. completedAt은 처음 저장한 시각(건너뛰기도 완료), 없으면 null
data class RecommendationPreferences(
    val fieldTagIds: List<Id>,
    val categories: List<ActivityCategory>,
    val traitTagIds: List<Id>,
    val completedAt: OffsetDateTime? = null,
) {
    val isEmpty: Boolean get() = fieldTagIds.isEmpty() && categories.isEmpty() && traitTagIds.isEmpty()

    companion object {
        val NONE = RecommendationPreferences(emptyList(), emptyList(), emptyList())
    }
}
