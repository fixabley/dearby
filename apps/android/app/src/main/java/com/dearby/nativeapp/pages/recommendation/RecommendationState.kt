package com.dearby.nativeapp.pages.recommendation

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 추천 (contract "추천 설정 퍼널·지원 자격 정보·추천", 2026-10-24). [available] is false until the recommendation API answered (404 before the server
 * ships it): then the profile rows hide; the funnel does not depend on it. [completed] is the funnel's completion (also `나중에`·끝까지 건너뛰기).
 * [eligibilitySummary] is the 지원 자격 정보 row's supporting text (`재학 중 · 3학년 · 2003년생`), null when nothing is entered.
 * [preferencesRead]: the signed-in settings read answered (or failed), so a `completedAt` from another device is already in [completed].
 */
data class RecommendationViewState(
    val available: Boolean = false, val localLoaded: Boolean = false, val completed: Boolean = false, val preferencesRead: Boolean = false,
    val eligibilitySummary: String? = null, val eligibilityConsented: Boolean = false, val eligibilityError: String? = null, val notice: String? = null,
) {
    /** The first signed-in visit to 발견 shows the funnel: decided by the completion flag (device, or the server's once read), never by the recommendation API. */
    val needsFunnel get() = localLoaded && preferencesRead && !completed
}

enum class FunnelStep { INTRO, FIELDS, SUBFIELDS, CATEGORIES, TRAITS, RESULT }
/** A choice; [icon] is the tag icon for 분야·성격 chips, null for 활동 종류 (a check shows while picked). */
data class ChipOption(val id: String, val label: String, val icon: ImageVector? = null)
/** A first-step 분야: an upper field with its [children], or a field without an upper field (no children, no sub-question). */
data class FieldNode(val option: ChipOption, val children: List<ChipOption>)

/**
 * The funnel's answers in progress; [first] is the first-entry run (starts at INTRO), otherwise it reopens at FIELDS from 설정.
 * 분야 (contract "추천 퍼널 분야 단계(상위 → 하위)"): [fields] are the first step's picks among [fieldTree]; [subFields] the children picked per
 * upper field (absent or empty = `{상위} 전부 좋아요`). [subIndex] is the sub-question screen shown while [step] is SUBFIELDS.
 */
data class RecommendationFunnelState(
    val step: FunnelStep, val first: Boolean, val fields: Set<String>, val categories: Set<String>, val traits: Set<String>,
    val subFields: Map<String, Set<String>> = emptyMap(), val subIndex: Int = 0,
    val fieldTree: List<FieldNode> = emptyList(), val traitOptions: List<ChipOption> = emptyList(), val tagsLoading: Boolean = true, val tagsFailed: Boolean = false,
    val saving: Boolean = false, val found: Int? = null, val eligibilityMissing: Boolean = true,
) {
    /** One screen per picked upper field, or all of them on one screen when 3 or more are picked. */
    val subScreens: List<List<FieldNode>> get() = fieldTree.filter { it.children.isNotEmpty() && it.option.id in fields }.let { if (it.size >= 3) listOf(it) else it.map(::listOf) }
    /** What is saved as `fieldTagIds`: an upper field's id for `전부 좋아요`, else its picked children; a field without children as itself. */
    val fieldTagIds: List<String> get() = fields.flatMap { id -> subFields[id]?.takeIf { it.isNotEmpty() } ?: setOf(id) }
    /** Progress over the questions (the sub-questions included) and the result; the intro is 0. */
    val progress: Float get() {
        val subs = subScreens.size
        val at = when (step) { FunnelStep.INTRO -> 0; FunnelStep.FIELDS -> 1; FunnelStep.SUBFIELDS -> 2 + subIndex; FunnelStep.CATEGORIES -> 2 + subs; FunnelStep.TRAITS -> 3 + subs; FunnelStep.RESULT -> 4 + subs }
        return at / (4f + subs)
    }
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
