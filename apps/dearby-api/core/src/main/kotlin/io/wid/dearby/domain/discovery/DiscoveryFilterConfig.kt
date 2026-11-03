package io.wid.dearby.domain.discovery

import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.ActivityStatus

// 발견 필터 시트 구성(admin-v1.md "발견 필터 구성", native-v1.md "발견 필터 시트 표현"): 어드민이 정하는 섹션 순서·켜기와 항목.
// 저장은 discovery_filter_config(id = 1).config jsonb 한 행이고, 행이 없으면 DEFAULT
data class DiscoveryFilterConfig(val sections: List<Section>) {

    // items: status는 세 묶음(코드 고정, 표시 이름만), category는 종류 코드, field는 분야 태그 id(비우면 상위 분야 전부, 사전 순), eligibility는 mine 하나
    data class Section(val key: String, val enabled: Boolean, val title: String, val subtitle: String? = null, val items: List<Item>? = null)

    data class Item(val code: String, val enabled: Boolean, val label: String, val icon: String)

    companion object {
        const val STATUS = "status"
        const val CATEGORY = "category"
        const val FIELD = "field"
        const val ELIGIBILITY = "eligibility"
        val SECTION_KEYS = listOf(STATUS, CATEGORY, FIELD, ELIGIBILITY)

        // 활동 상태 세 묶음(native-v1.md "활동 상태"): 묶음 id는 저장값·URL·사용 통계와 같다
        val STATUS_GROUPS: Map<String, Set<ActivityStatus>> = linkedMapOf(
            "available" to setOf(ActivityStatus.RECRUITING_UPCOMING, ActivityStatus.RECRUITING),
            "in_progress" to setOf(ActivityStatus.IN_PROGRESS, ActivityStatus.RECRUITMENT_CLOSED, ActivityStatus.NO_RECRUITMENT),
            "past" to setOf(ActivityStatus.ENDED),
        )
        const val ELIGIBILITY_MINE = "mine"

        // 기본값(행이 없을 때): 상태 세 묶음, 종류 여섯(추천 설정 퍼널의 순서·아이콘), 분야 상위 전부, 대상 켬
        val DEFAULT = DiscoveryFilterConfig(
            listOf(
                Section(
                    STATUS, true, "활동 상태", "여러 개 고를 수 있어요",
                    listOf(Item("available", true, "지원 가능", "send"), Item("in_progress", true, "진행·예정", "calendar"), Item("past", true, "지난 활동", "history")),
                ),
                Section(
                    CATEGORY, true, "종류", null,
                    listOf(
                        category(ActivityCategory.BOOTCAMP, "부트캠프"), category(ActivityCategory.CLUB, "연합동아리"), category(ActivityCategory.CONTEST, "대회"),
                        category(ActivityCategory.HACKATHON, "해커톤"), category(ActivityCategory.CONFERENCE, "컨퍼런스"), category(ActivityCategory.STUDY, "스터디"),
                    ),
                ),
                Section(FIELD, true, "분야", null, emptyList()),
                Section(ELIGIBILITY, true, "대상", null, listOf(Item(ELIGIBILITY_MINE, true, "내 조건에 맞는 활동만", "profile"))),
            ),
        )

        private fun category(category: ActivityCategory, label: String) = Item(category.name, true, label, Icons.ofCategory(category))
    }
}
