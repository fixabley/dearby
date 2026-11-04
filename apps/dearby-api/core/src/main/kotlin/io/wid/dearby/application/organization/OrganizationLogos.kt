package io.wid.dearby.application.organization

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.OrganizationLogo
import org.springframework.stereotype.Component

// 조직 화면·팔로우 목록·검색 칩에 보일 조직 로고(native-v1.md "조직 로고와 로고 계승"): 자기 로고, 없으면 가장 가까운 상위 조직 로고(최대 5단계). 없는 조직은 결과에서 빠진다
@Component
class OrganizationLogos(private val organizations: OrganizationRepository, private val logos: OrganizationLogoRepository) {

    data class Found(val url: String, val brandColor: String?)

    fun of(ids: Collection<Id>): Map<Id, Found> {
        if (ids.isEmpty()) return emptyMap()
        val ancestors = organizations.ancestors(ids.toSet())
        val chains = ids.associateWith { id -> (listOf(id) + ancestors[id].orEmpty().reversed().map { it.id }).take(MAX_CHAIN) }
        val updatedAt = logos.updatedAtByOrganization(chains.values.flatten().toSet())
        val colors = logos.brandColors(updatedAt.keys)
        return chains.mapNotNull { (id, chain) ->
            val found = chain.firstOrNull { it in updatedAt } ?: return@mapNotNull null
            id to Found(OrganizationLogo.url(found, updatedAt.getValue(found)), colors[found])
        }.toMap()
    }

    private companion object {
        const val MAX_CHAIN = 5
    }
}
