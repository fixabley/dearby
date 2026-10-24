package io.wid.dearby.application.recommendation

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.recommendation.EligibilityProfile
import io.wid.dearby.domain.recommendation.RecommendationPreferences

// 저장소 포트. 구현은 adaptor/persistence. 두 표 모두 users FK cascade라 계정 삭제와 함께 지워진다
interface RecommendationRepository {
    fun findPreferences(userId: UserId): RecommendationPreferences?
    // 있으면 덮어쓰되 completed_at은 처음 저장한 시각을 지킨다
    fun savePreferences(userId: UserId, preferences: RecommendationPreferences)

    fun findEligibility(userId: UserId): EligibilityProfile?
    fun saveEligibility(userId: UserId, profile: EligibilityProfile)
    fun deleteEligibility(userId: UserId)

    // 활동 분야 태그의 수집 연관도(0~100): 활동 id → 태그 id → 연관도(운영자 지정은 null)
    fun fieldRelevance(activityIds: Collection<Id>): Map<Id, Map<Id, Int?>>
}
