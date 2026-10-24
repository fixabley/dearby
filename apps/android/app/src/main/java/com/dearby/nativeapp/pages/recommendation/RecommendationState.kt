package com.dearby.nativeapp.pages.recommendation

/**
 * 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천", 2026-10-24). [available] is false until the recommendation API answered (404 before the server
 * ships it): then the profile rows hide; the funnel does not depend on it. [completed] is the funnel's completion (also `나중에`·끝까지 건너뛰기).
 * [eligibilitySummary] is the 지원 자격 정보 row's supporting text (`재학 중 · 3학년 · 2003년생`), null when nothing is entered.
 */
data class RecommendationViewState(
    val available: Boolean = false, val localLoaded: Boolean = false, val completed: Boolean = false,
    val eligibilitySummary: String? = null, val eligibilityConsented: Boolean = false, val eligibilityError: String? = null, val notice: String? = null,
) {
    /** The first visit to 발견 shows the funnel: decided by the device's completion flag alone, never by the recommendation API. */
    val needsFunnel get() = localLoaded && !completed
}

enum class FunnelStep { INTRO, FIELDS, CATEGORIES, TRAITS, RESULT }
data class ChipOption(val id: String, val label: String)
data class ChipGroup(val title: String, val options: List<ChipOption>)

/** The funnel's answers in progress; [first] is the first-entry run (starts at INTRO), otherwise it reopens at FIELDS from 설정. */
data class RecommendationFunnelState(
    val step: FunnelStep, val first: Boolean, val fields: Set<String>, val categories: Set<String>, val traits: Set<String>,
    val fieldGroups: List<ChipGroup> = emptyList(), val traitOptions: List<ChipOption> = emptyList(), val tagsLoading: Boolean = true, val tagsFailed: Boolean = false,
    val saving: Boolean = false, val found: Int? = null, val eligibilityMissing: Boolean = true,
) {
    /** Progress over the three questions and the result; the intro is 0. */
    val progress get() = step.ordinal / (FunnelStep.entries.size - 1f)
}

/** 상황 (contract): the choice → `educationStatus`/`employment`; [hasYear] asks 학년 next. [short] is how the row's summary says it. */
enum class EducationChoice(val label: String, val short: String, val educationStatus: String?, val employment: String?, val hasYear: Boolean) {
    ENROLLED("재학 중", "재학 중", "enrolled", "student", true), ON_LEAVE("휴학 중", "휴학 중", "onLeave", "student", true),
    GRADUATING("졸업 예정", "졸업 예정", "graduating", "student", true), GRADUATED("졸업했어요", "졸업", "graduated", null, false),
    EMPLOYED("직장에 다녀요", "직장인", null, "employed", false),
}
enum class EligibilityStep { STATUS, YEAR, BIRTH }
/** One question per screen after consent; [years] is the birth-year wheel's range. */
data class EligibilityFormState(
    val step: EligibilityStep, val choice: EducationChoice?, val schoolYear: Int?, val birthYear: Int, val years: IntRange,
    val saving: Boolean = false, val error: String? = null,
) {
    val steps get() = if (choice?.hasYear == false) listOf(EligibilityStep.STATUS, EligibilityStep.BIRTH) else EligibilityStep.entries
}
