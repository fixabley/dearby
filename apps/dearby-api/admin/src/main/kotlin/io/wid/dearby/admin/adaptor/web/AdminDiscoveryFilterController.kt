package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.application.discovery.DiscoveryFilterService
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.discovery.DiscoveryFilterConfig
import io.wid.dearby.domain.discovery.Icons
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 발견 필터 구성(admin-v1.md "발견 필터 구성"): 섹션 순서·켜기, 종류·분야 항목의 노출·순서·표시 이름·아이콘. ADMIN만(AdminAccessRules).
// 저장 즉시 공개 GET /v1/discovery/filters에 반영된다(HTTP 캐시 5분)
@RestController
@RequestMapping("/v1/admin")
class AdminDiscoveryFilterController(private val filters: DiscoveryFilterService) {

    // 저장된 구성, 없으면 기본 구성
    @GetMapping("/discovery-filters")
    fun get() = DiscoveryFilterConfigResponse.from(filters.config())

    // 모르는 섹션 키·종류 코드·아이콘 키·없는 분야 태그 id는 400
    @PutMapping("/discovery-filters")
    fun put(@RequestBody request: DiscoveryFilterConfigRequest) = DiscoveryFilterConfigResponse.from(filters.save(request.toConfig()))

    // 앱에 내장된 "아이콘 통일" 표의 키 목록(아이콘 선택기). 서버 상수
    @GetMapping("/icons")
    fun icons() = IconListResponse(Icons.ALL.map { IconResponse(it.key, it.label, it.ios, it.android) })
}

data class DiscoveryFilterConfigRequest(val sections: List<SectionRequest>? = null) {
    fun toConfig() = DiscoveryFilterConfig(
        (sections ?: throw InvalidInputException("sections가 필요합니다")).map { section ->
            DiscoveryFilterConfig.Section(
                key = section.key?.trim()?.takeIf { it.isNotEmpty() } ?: throw InvalidInputException("섹션 key가 필요합니다"),
                enabled = section.enabled ?: true,
                title = section.title?.trim().orEmpty(),
                subtitle = section.subtitle?.trim()?.ifEmpty { null },
                items = section.items?.map { item ->
                    DiscoveryFilterConfig.Item(
                        code = item.code?.trim()?.takeIf { it.isNotEmpty() } ?: throw InvalidInputException("항목 code가 필요합니다"),
                        enabled = item.enabled ?: true,
                        label = item.label?.trim().orEmpty(),
                        icon = item.icon?.trim().orEmpty(),
                    )
                },
            )
        },
    )
}

data class SectionRequest(val key: String? = null, val enabled: Boolean? = null, val title: String? = null, val subtitle: String? = null, val items: List<ItemRequest>? = null)

data class ItemRequest(val code: String? = null, val enabled: Boolean? = null, val label: String? = null, val icon: String? = null)

// 요청과 같은 모양: {sections: [{key, enabled, title, subtitle?, items?: [{code, enabled, label, icon}]}]}
data class DiscoveryFilterConfigResponse(val sections: List<SectionResponse>) {
    companion object {
        fun from(config: DiscoveryFilterConfig) = DiscoveryFilterConfigResponse(
            config.sections.map { SectionResponse(it.key, it.enabled, it.title, it.subtitle, it.items?.map { item -> ItemResponse(item.code, item.enabled, item.label, item.icon) }) },
        )
    }
}

data class SectionResponse(val key: String, val enabled: Boolean, val title: String, val subtitle: String?, val items: List<ItemResponse>?)

data class ItemResponse(val code: String, val enabled: Boolean, val label: String, val icon: String)

data class IconListResponse(val items: List<IconResponse>)

// key: 구성에 저장하는 값. ios: SF Symbol 이름, android: Icons.Rounded 이름. 미리 보기는 어드민의 같은 이름 인라인 SVG
data class IconResponse(val key: String, val label: String, val ios: String, val android: String)
