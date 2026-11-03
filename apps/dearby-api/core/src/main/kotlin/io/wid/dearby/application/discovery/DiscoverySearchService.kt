package io.wid.dearby.application.discovery

import io.wid.dearby.application.favorite.FavoriteService
import io.wid.dearby.application.organization.OrganizationLogos
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.EffectiveLogos
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 필터 시트의 조직·프로그램 검색(native-v1.md "발견 필터 시트 표현" GET /v1/discovery/search): 이름·영문 이름 부분 일치, 팔로우한 것 먼저, 최대 20.
// 항목 type은 organization·program 둘이고 뒤에 activity를 더할 수 있게 열어 둔다
@Service
class DiscoverySearchService(
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val organizationLogos: OrganizationLogos,
    private val programLogos: EffectiveLogos,
    private val favorites: FavoriteService,
) {

    enum class Type { ORGANIZATION, PROGRAM }

    data class Item(val type: Type, val id: Id, val name: String, val nameEn: String?, val logoUrl: String?, val category: ActivityCategory?, val followed: Boolean)

    @Transactional(readOnly = true)
    fun search(q: String, userId: UserId?): List<Item> {
        val query = q.trim()
        if (query.length > MAX_QUERY_LENGTH) throw InvalidInputException("검색어는 ${MAX_QUERY_LENGTH}자까지입니다")
        if (query.isEmpty()) return emptyList()
        val mine = userId?.let { favorites.ids(it) }
        val foundOrganizations = organizations.search(query, null, rootsOnly = false, after = null, size = MAX_ITEMS)
        val foundPrograms = programs.search(query, null, after = null, size = MAX_ITEMS)
        val orgLogo = organizationLogos.of(foundOrganizations.map { it.id })
        val programLogo = programLogos.of(foundPrograms.associate { it.id to it.organizationId })
        val items = foundOrganizations.map {
            Item(Type.ORGANIZATION, it.id, it.name, it.nameEn, orgLogo[it.id]?.url, null, mine?.organizations?.contains(it.id) == true)
        } + foundPrograms.map {
            Item(Type.PROGRAM, it.id, it.name, it.nameEn, programLogo[it.id]?.url, it.category, mine?.programs?.contains(it.id) == true)
        }
        // 팔로우한 것 먼저, 그 뒤 이름 앞부분 일치 → 이름 순
        return items.sortedWith(compareByDescending<Item> { it.followed }.thenByDescending { it.name.startsWith(query, ignoreCase = true) || it.nameEn?.startsWith(query, ignoreCase = true) == true }.thenBy { it.name }).take(MAX_ITEMS)
    }

    companion object {
        const val MAX_ITEMS = 20
        const val MAX_QUERY_LENGTH = 100
    }
}
