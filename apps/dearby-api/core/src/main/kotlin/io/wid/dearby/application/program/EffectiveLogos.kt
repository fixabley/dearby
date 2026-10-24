package io.wid.dearby.application.program

import io.wid.dearby.application.organization.OrganizationLogoRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.domain.EffectiveLogo
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.LogoSource
import io.wid.dearby.domain.OrganizationLogo
import io.wid.dearby.domain.ProgramLogo
import org.springframework.stereotype.Component

// 로고 계승(native-v1.md "조직 로고와 로고 계승"): 프로그램 로고 → 조직 → 상위 조직 … (최대 5단계) 중 처음 있는 것.
// 목록 하나에 쿼리 몇 번으로 끝난다(프로그램 로고, 상위 경로, 조직 로고)
@Component
class EffectiveLogos(
    private val programLogos: ProgramLogoRepository,
    private val organizations: OrganizationRepository,
    private val organizationLogos: OrganizationLogoRepository,
) {

    // organizationIdByProgram: 프로그램 id → 그 프로그램의 조직 id. 로고가 하나도 없는 프로그램은 결과에서 빠진다
    fun of(organizationIdByProgram: Map<Id, Id>): Map<Id, EffectiveLogo> {
        if (organizationIdByProgram.isEmpty()) return emptyMap()
        val own = programLogos.updatedAtByProgram(organizationIdByProgram.keys)
        val ownColors = programLogos.brandColors(own.keys)
        val needed = organizationIdByProgram.filterKeys { it !in own }
        val orgIds = needed.values.toSet()
        val names = organizations.findAllById(orgIds).associate { it.id to it.name }
        val ancestors = organizations.ancestors(orgIds)
        // 조직별 사슬: 자기 자신부터 가까운 상위 순, 최대 5개
        val chains = orgIds.associateWith { id -> (listOf(id to names[id]) + ancestors[id].orEmpty().reversed()).take(MAX_CHAIN) }
        val orgLogos = organizationLogos.updatedAtByOrganization(chains.values.flatten().map { it.first }.toSet())
        val orgColors = organizationLogos.brandColors(orgLogos.keys)
        return organizationIdByProgram.mapNotNull { (programId, organizationId) ->
            own[programId]?.let { return@mapNotNull programId to EffectiveLogo(ProgramLogo.url(programId, it), LogoSource.PROGRAM, null, null, ownColors[programId]) }
            val (id, name) = chains[organizationId]?.firstOrNull { it.first in orgLogos } ?: return@mapNotNull null
            programId to EffectiveLogo(OrganizationLogo.url(id, orgLogos.getValue(id)), LogoSource.ORGANIZATION, id, name, orgColors[id])
        }.toMap()
    }

    private companion object {
        const val MAX_CHAIN = 5
    }
}
