package io.wid.dearby.application.discovery

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.discovery.ActivityRef
import io.wid.dearby.domain.discovery.Engagement
import kotlin.math.exp

// 관심 조직 가중치(조직 친화도, native-v1.md "관심 조직 가중치"): 조직별 S = 그 조직 프로그램들의 감쇠 신호 합(부정 포함) + 프로그램 하트.
// 조직 가중치 = 1 − e^(−S), 상위 조직 가중치 = 1 − e^(−0.5 × 하위 조직들의 S 합)(음수 S는 0). 하위 조직이 10개를 넘는 넓은 상위 조직은 쓰지 않는다
class Affinity private constructor(
    val organizations: Map<Id, Double>, // 조직 가중치(0~1, 저장)
    private val groupWeights: Map<Id, Double>, // 상위 조직 가중치(0~1)
    private val engaged: Map<Id, Double>, // 프로그램별 가장 강한 내 활동 가중치(새 기수 이유)
    private val parentOf: Map<Id, Id>, // 쓸 수 있는 상위 조직
    private val names: Map<Id, String>,
) {
    // 후보 활동의 관심 조직 가중치(조직·상위 조직 중 큰 것)와 이유 하나
    fun of(listing: ActivityListing): Pair<Double, String?> {
        val organizationId = listing.organization.id
        val own = organizations[organizationId] ?: 0.0
        val parentId = parentOf[organizationId]
        val parent = parentId?.let { groupWeights[it] } ?: 0.0
        val reason = when {
            (engaged[listing.program.id] ?: 0.0) >= Engagement.APPLIED.weight -> "지난 기수에 지원했어요"
            listing.program.id in engaged -> "내 활동에 담았던 프로그램"
            own >= REASON_MIN && own >= parent -> "${listing.organization.name}에 관심을 보였어요"
            parent >= REASON_MIN -> names[parentId]?.let { "같은 $it 그룹" }
            else -> null
        }
        return maxOf(own, parent) to reason
    }

    companion object {
        private const val PARENT_FACTOR = 0.5
        private const val MAX_CHILDREN = 10
        private const val REASON_MIN = 0.3

        // weights: 활동 → 감쇠한 신호 가중치 합, refs: 그 활동들의 소속, engaged: 프로그램 → 내 활동 가중치,
        // programSignals: 프로그램 → (조직, 감쇠한 프로그램 하트 +0.6, native-v1.md "추천과 하나로")
        fun of(
            weights: Map<Id, Double>, refs: Map<Id, ActivityRef>, engaged: Map<Id, Double>, organizations: OrganizationRepository,
            programSignals: Map<Id, Pair<Id, Double>> = emptyMap(),
        ): Affinity {
            val sums = mutableMapOf<Id, Double>() // 조직별 S
            weights.forEach { (activityId, w) -> refs[activityId]?.let { sums.merge(it.organizationId, w, Double::plus) } }
            programSignals.values.forEach { (organizationId, w) -> sums.merge(organizationId, w, Double::plus) }
            val orgs = organizations.findAllById(sums.keys)
            val parents = orgs.mapNotNull { o -> o.parentOrganizationId?.let { o.id to it } }.toMap()
            val wide = organizations.childCounts(parents.values.toSet()).filterValues { it > MAX_CHILDREN }.keys
            val parentOf = parents.filterValues { it !in wide }
            val names = (orgs + organizations.findAllById(parentOf.values.toSet())).associate { it.id to it.name }
            val groupSums = mutableMapOf<Id, Double>()
            parentOf.forEach { (organizationId, parentId) -> groupSums.merge(parentId, sums.getValue(organizationId), Double::plus) }
            fun saturate(x: Double) = 1 - exp(-x.coerceAtLeast(0.0)) // 음수 S는 0
            return Affinity(
                organizations = sums.mapValues { saturate(it.value) }.filterValues { it > 0 },
                groupWeights = groupSums.mapValues { saturate(PARENT_FACTOR * it.value) },
                engaged = engaged, parentOf = parentOf, names = names,
            )
        }
    }
}
