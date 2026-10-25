package io.wid.dearby.application.discovery

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.discovery.ActivityRef
import io.wid.dearby.domain.discovery.Engagement
import kotlin.math.exp

// 조직 친화도(native-v1.md "조직 친화도"): 활동 신호를 프로그램(×1.0) → 조직(×0.6) → 상위 조직(×0.3)으로 올려 더하고 1 − e^(−합)으로 0~1에 맞춘다.
// 하위 조직이 10개를 넘는 넓은 상위 조직으로는 올리지 않는다
class Affinity private constructor(
    val organizations: Map<Id, Double>, // 저장할 조직 친화도(0~1)
    private val programs: Map<Id, Double>, // 같은 프로그램 가산(0~1)
    private val engaged: Map<Id, Double>, // 프로그램별 가장 강한 내 활동 가중치(새 기수 이유)
    private val parentOf: Map<Id, Id>, // 올려 보낼 수 있는 상위 조직
    private val names: Map<Id, String>,
) {

    // 후보 활동의 친화도(조직·상위 조직 중 큰 것 + 같은 프로그램 가산)와 이유 하나
    fun of(listing: ActivityListing): Pair<Double, String?> {
        val organizationId = listing.organization.id
        val own = organizations[organizationId] ?: 0.0
        val parentId = parentOf[organizationId]
        val parent = parentId?.let { organizations[it] } ?: 0.0
        val program = programs[listing.program.id] ?: 0.0
        val reason = when {
            (engaged[listing.program.id] ?: 0.0) >= Engagement.APPLIED.weight -> "지난 기수에 지원했어요"
            listing.program.id in engaged -> "내 활동에 담았던 프로그램"
            own >= REASON_MIN && own >= parent -> "${listing.organization.name}에 관심을 보였어요"
            parent >= REASON_MIN -> names[parentId]?.let { "같은 $it 그룹" }
            else -> null
        }
        return (maxOf(own, parent) + program) to reason
    }

    companion object {
        private const val ORGANIZATION_FACTOR = 0.6
        private const val PARENT_FACTOR = 0.3
        private const val MAX_CHILDREN = 10
        private const val REASON_MIN = 0.3

        // weights: 활동 → 감쇠한 신호 가중치 합, refs: 그 활동들의 소속, engaged: 프로그램 → 내 활동 가중치,
        // programSignals: 프로그램 → (조직, 감쇠한 프로그램 신호). 프로그램 하트(native-v1.md "추천과 하나로": +0.6)
        fun of(
            weights: Map<Id, Double>, refs: Map<Id, ActivityRef>, engaged: Map<Id, Double>, organizations: OrganizationRepository,
            programSignals: Map<Id, Pair<Id, Double>> = emptyMap(),
        ): Affinity {
            val byProgram = mutableMapOf<Id, Double>()
            val orgOfProgram = mutableMapOf<Id, Id>()
            weights.forEach { (activityId, w) -> refs[activityId]?.let { byProgram.merge(it.programId, w, Double::plus); orgOfProgram[it.programId] = it.organizationId } }
            programSignals.forEach { (programId, signal) -> byProgram.merge(programId, signal.second, Double::plus); orgOfProgram[programId] = signal.first }
            val orgIds = orgOfProgram.values.toSet()
            val orgs = organizations.findAllById(orgIds)
            val parents = orgs.mapNotNull { o -> o.parentOrganizationId?.let { o.id to it } }.toMap()
            val wide = organizations.childCounts(parents.values.toSet()).filterValues { it > MAX_CHILDREN }.keys
            val parentOf = parents.filterValues { it !in wide }
            val names = (orgs + organizations.findAllById(parentOf.values.toSet())).associate { it.id to it.name }
            val raw = mutableMapOf<Id, Double>()
            byProgram.forEach { (programId, w) ->
                val organizationId = orgOfProgram.getValue(programId)
                raw.merge(organizationId, ORGANIZATION_FACTOR * w, Double::plus)
                parentOf[organizationId]?.let { raw.merge(it, PARENT_FACTOR * w, Double::plus) }
            }
            fun saturate(x: Double) = if (x > 0) 1 - exp(-x) else 0.0
            return Affinity(
                organizations = raw.mapValues { saturate(it.value) }.filterValues { it > 0 },
                programs = byProgram.mapValues { saturate(it.value) }.filterValues { it > 0 },
                engaged = engaged, parentOf = parentOf, names = names,
            )
        }
    }
}
