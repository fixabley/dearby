package io.wid.dearby.domain.eligibility

// 대상자 조건의 어휘(native-v1.md "대상자 조건"). 한글이 기준이고 영문(camelCase, JSON 키·쿼리 키와 같은 표기)을 함께 저장한다. DB 열 이름만 snake_case다.
// NUMERIC은 from·to 범위, SET은 values(한글)·valuesEn(영문), NOTE(기타)는 note만 가진다
enum class EligibilityField(val ko: String, val en: String, val kind: Kind, val valueVocabulary: List<Pair<String, String>> = emptyList()) {
    AGE("나이", "age", Kind.NUMERIC),
    BIRTH_YEAR("출생연도", "birthYear", Kind.NUMERIC),
    SCHOOL_YEAR("학년", "schoolYear", Kind.NUMERIC),
    EDUCATION_LEVEL("학력", "educationLevel", Kind.SET, listOf("고졸" to "highSchool", "대학" to "university", "대학원" to "graduateSchool")),
    EDUCATION_STATUS("재학상태", "educationStatus", Kind.SET, listOf("재학" to "enrolled", "졸업" to "graduated", "휴학" to "onLeave", "졸업예정" to "graduating")),
    MAJOR("전공", "major", Kind.SET),
    REGION("지역", "region", Kind.SET),
    EMPLOYMENT("취업상태", "employment", Kind.SET, listOf("학생" to "student", "미취업" to "unemployed", "재직" to "employed", "창업" to "founder")),
    EXPERIENCE_YEARS("경력연수", "experienceYears", Kind.NUMERIC),
    NATIONALITY("국적", "nationality", Kind.SET), // ISO 3166-1 alpha-2
    TEAM_SIZE("팀규모", "teamSize", Kind.NUMERIC),
    OTHER("기타", "other", Kind.NOTE),
    ;

    enum class Kind { NUMERIC, SET, NOTE }

    // 고정 어휘가 있는 값은 한글 ↔ 영문을 서로 채운다. 없는 값은 그대로 둔다
    fun valueEn(ko: String): String? = valueVocabulary.firstOrNull { it.first == ko }?.second
    fun valueKo(en: String): String? = valueVocabulary.firstOrNull { it.second.equals(en, ignoreCase = true) }?.first

    companion object {
        fun byKo(ko: String): EligibilityField? = entries.firstOrNull { it.ko == ko.trim() }
        fun byEn(en: String): EligibilityField? = entries.firstOrNull { it.en.equals(en.trim(), ignoreCase = true) }

        // 검색 쿼리 키로 쓰는 어휘(기타는 검색하지 않는다)
        val searchable: List<EligibilityField> = entries.filter { it.kind != Kind.NOTE }
    }
}
