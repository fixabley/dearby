package io.wid.dearby.domain

// 활동 종류(native-v1.md "활동 종류(category)", 2026-10-19). 순서가 화면의 묶음 순서다. 한글 이름은 클라이언트가 붙인다
enum class ActivityCategory {
    BOOTCAMP, CONFERENCE, WORKSHOP, STUDY, CLUB, LECTURE, HACKATHON, CONTEST, MEETUP, OTHER;

    companion object {
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
