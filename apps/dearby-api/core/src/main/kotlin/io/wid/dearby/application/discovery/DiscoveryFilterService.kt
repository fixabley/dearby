package io.wid.dearby.application.discovery

import io.wid.dearby.application.program.ProgramScope
import io.wid.dearby.application.program.ProgramSummaryService
import io.wid.dearby.application.program.ProgramSummarySearch
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.DiscoveryKind
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.TagKind
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig.Companion.CATEGORY
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig.Companion.ELIGIBILITY
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig.Companion.ELIGIBILITY_MINE
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig.Companion.FIELD
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig.Companion.STATUS
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig.Companion.STATUS_GROUPS
import io.wid.dearby.domain.discovery.Icons
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 발견 필터 시트 구성(admin-v1.md "발견 필터 구성")과 공개 응답(native-v1.md "발견 필터 시트 표현" GET /v1/discovery/filters).
// 어드민이 저장한 구성(없으면 기본)에 현재 kind·검색어·조직/프로그램 칩 기준의 프로그램 수를 붙여 앱이 그대로 그린다
@Service
class DiscoveryFilterService(
    private val configs: DiscoveryFilterRepository,
    private val tags: TagRepository,
    private val summaries: ProgramSummaryService,
) {

    // 공개 응답의 섹션. count: 그 조건의 프로그램 수(eligibility의 mine은 사용자별이라 공개 응답에서는 null)
    data class Section(val key: String, val title: String, val subtitle: String?, val multiple: Boolean, val items: List<Item>)
    data class Item(val code: String, val label: String, val icon: String, val count: Int?)

    @Transactional(readOnly = true)
    fun config(): DiscoveryFilterConfig = configs.find() ?: DiscoveryFilterConfig.DEFAULT

    // 모르는 섹션 키·종류 코드·아이콘 키·없는 분야 태그 id는 400. 섹션 키는 한 번씩만
    @Transactional
    fun save(config: DiscoveryFilterConfig): DiscoveryFilterConfig {
        val keys = config.sections.map { it.key }
        keys.firstOrNull { it !in DiscoveryFilterConfig.SECTION_KEYS }?.let { throw InvalidInputException("모르는 필터 섹션입니다: $it") }
        if (keys.size != keys.distinct().size) throw InvalidInputException("필터 섹션이 겹칩니다")
        val fieldTags = tags.findAll().filter { it.kind == TagKind.FIELD }.associateBy { it.id }
        config.sections.forEach { section ->
            if (section.title.isBlank()) throw InvalidInputException("섹션 이름을 입력해 주세요")
            section.items.orEmpty().forEach { item ->
                if (item.label.isBlank()) throw InvalidInputException("항목 이름을 입력해 주세요: ${item.code}")
                if (!Icons.has(item.icon)) throw InvalidInputException("모르는 아이콘입니다: ${item.icon}")
                when (section.key) {
                    STATUS -> if (item.code !in STATUS_GROUPS) throw InvalidInputException("모르는 활동 상태 묶음입니다: ${item.code}")
                    CATEGORY -> ActivityCategory.parse(item.code)
                    FIELD -> if (item.code.toLongOrNull() !in fieldTags) throw InvalidInputException("없는 분야 태그입니다: ${item.code}")
                    ELIGIBILITY -> if (item.code != ELIGIBILITY_MINE) throw InvalidInputException("대상 섹션의 항목은 $ELIGIBILITY_MINE 하나입니다")
                }
            }
        }
        configs.save(config, OffsetDateTime.now(ZoneOffset.UTC))
        return config
    }

    // 켜진 섹션만, 구성 순서대로. 항목은 켜진 것만. 분야 항목이 비어 있으면 상위 분야 전부(사전 순)
    @Transactional(readOnly = true)
    fun sections(q: String?, kind: DiscoveryKind?, scope: ProgramScope?): List<Section> {
        val facets = summaries.facets(ProgramSummarySearch(q, null, 0, 1, kind = kind, scope = summaries.expand(scope)))
        val fieldTags = tags.findAll().filter { it.kind == TagKind.FIELD }
        val childrenOf = fieldTags.filter { it.parentId != null }.groupBy({ it.parentId!! }, { it.id })
        // 묶음·종류·분야마다 프로그램 수. 종류·분야는 지원 가능(모집 중·예정) 활동 기준
        val available = STATUS_GROUPS.getValue("available")
        val byStatusGroup = STATUS_GROUPS.mapValues { (_, statuses) -> facets.filter { it.status in statuses }.distinctBy { it.programId }.size }
        val open = facets.filter { it.status in available }
        val byCategory = open.filter { it.category != null }.groupBy { it.category!! }.mapValues { (_, list) -> list.distinctBy { it.programId }.size }
        fun fieldCount(tagId: Id): Int {
            val ids = setOf(tagId) + childrenOf[tagId].orEmpty()
            return open.filter { facet -> facet.tagIds.any { it in ids } }.distinctBy { it.programId }.size
        }
        return config().sections.filter { it.enabled }.map { section ->
            val items = when (section.key) {
                STATUS -> section.items.orEmpty().filter { it.enabled }.map { Item(it.code, it.label, it.icon, byStatusGroup[it.code] ?: 0) }
                CATEGORY -> section.items.orEmpty().filter { it.enabled }.map { Item(it.code, it.label, it.icon, byCategory[ActivityCategory.valueOf(it.code)] ?: 0) }
                FIELD -> {
                    val chosen = section.items.orEmpty().filter { it.enabled }
                    if (chosen.isEmpty()) fieldTags.filter { it.parentId == null }.map { Item(it.id.toString(), it.name, Icons.ofTag(it.nameEn), fieldCount(it.id)) }
                    else chosen.map { Item(it.code, it.label, it.icon, fieldCount(it.code.toLong())) }
                }
                else -> section.items.orEmpty().filter { it.enabled }.map { Item(it.code, it.label, it.icon, null) }
            }
            Section(section.key, section.title, section.subtitle, multiple = section.key != ELIGIBILITY, items = items)
        }
    }
}
