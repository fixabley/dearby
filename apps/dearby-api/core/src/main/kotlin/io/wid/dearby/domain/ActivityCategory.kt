package io.wid.dearby.domain

// 활동 종류(native-v1.md "활동 종류(category)", 2026-10-19). 순서가 화면의 묶음 순서다. 한글 이름은 클라이언트가 붙인다
enum class ActivityCategory {
    BOOTCAMP, CONFERENCE, WORKSHOP, STUDY, CLUB, LECTURE, HACKATHON, CONTEST, MEETUP, OTHER;

    companion object {
        // 단기 행사 종류(native-v1.md "발견 프로그램·이벤트 전환", "활동 이력에서 단기 행사 제외"). 발견의 kind=event와 이력 후보 판정이 함께 쓴다
        val EVENTS: Set<ActivityCategory> = setOf(CONFERENCE, WORKSHOP, MEETUP, LECTURE)
        private val names = entries.joinToString(", ") { it.name }

        // 코드 하나(대소문자 무시). 모르는 값은 400
        fun parse(code: String): ActivityCategory =
            entries.firstOrNull { it.name.equals(code.trim(), ignoreCase = true) }
                ?: throw InvalidInputException("category는 $names 중 하나입니다")

        // 필터: 코드 하나 또는 쉼표 목록. 비어 있으면 null(필터 없음)
        fun parseList(value: String?): Set<ActivityCategory>? =
            value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }?.map(::parse)?.toSet()
    }
}

// 발견 화면의 프로그램·이벤트 전환(native-v1.md "발견 프로그램·이벤트 전환", 2026-10-26). EVENT는 단기 행사 종류, PROGRAM은 그 밖의 종류와 미분류(null)
enum class DiscoveryKind {
    PROGRAM, EVENT;

    companion object {
        // 생략하면 null(전환 없음, 옛 앱 호환). 모르는 값은 400
        fun parse(value: String?): DiscoveryKind? = value?.trim()?.takeIf { it.isNotEmpty() }?.let { code ->
            entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: throw InvalidInputException("kind는 program 또는 event입니다")
        }
    }
}
