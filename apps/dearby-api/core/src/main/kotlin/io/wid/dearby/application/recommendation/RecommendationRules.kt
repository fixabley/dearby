package io.wid.dearby.application.recommendation

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.program.ProgramSummaryService
import io.wid.dearby.domain.Activity
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.TagKind
import io.wid.dearby.domain.recommendation.RecommendationPreferences
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

// 추천 규칙 점수(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"). 추천 API와 맞춤 대기열이 같은 점수·이유를 쓴다
@Component
class RecommendationRules(
    private val recommendations: RecommendationRepository,
    private val summaries: ProgramSummaryService,
) {

    class Scored(val listing: ActivityListing, val points: Int, val matched: Boolean, val unchecked: Boolean, val deadline: OffsetDateTime?, val reasons: List<String>)

    // flagUnchecked: 지원 자격 정보가 있을 때만 확인하지 않은 활동을 알린다
    fun score(candidates: List<ActivityListing>, preferences: RecommendationPreferences, flagUnchecked: Boolean): List<Scored> {
        val relevance = recommendations.fieldRelevance(candidates.map { it.activity.id })
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return candidates.map { score(it, preferences, relevance[it.activity.id].orEmpty(), now, flagUnchecked) }
    }

    // 분야(하위 분야 일치 > 상위 분야로 일치, 수집 연관도 가중) + 종류 + 성격 일치 수 + 마감 임박. 점수는 내보내지 않고 이유만 보인다
    private fun score(listing: ActivityListing, preferences: RecommendationPreferences, relevance: Map<Long, Int?>, now: OffsetDateTime, flagUnchecked: Boolean): Scored {
        val activity = listing.activity
        val reasons = mutableListOf<String>()
        // 활동 분야 태그(없으면 프로그램 분야 태그)마다: 고른 분야와 같으면 1, 고른 분야의 하위면 0.6
        val fields = activity.fieldTags.mapNotNull { tag ->
            val weight = when {
                tag.id in preferences.fieldTagIds -> 1.0
                tag.parentId != null && tag.parentId in preferences.fieldTagIds -> PARENT_MATCH_WEIGHT
                else -> return@mapNotNull null
            }
            // 활동에 붙은 태그는 수집 연관도(운영자 지정은 연관도 없음), 프로그램에서 물려받은 태그는 약하게
            val score = if (tag.id in relevance) relevance[tag.id] ?: CURATED_RELEVANCE else PROGRAM_RELEVANCE
            tag to (FIELD_POINTS * weight * score / 100).roundToInt()
        }.sortedByDescending { it.second }
        fields.take(MAX_FIELD_REASONS).forEach { reasons += it.first.name }
        val category = activity.category?.takeIf { it in preferences.categories }?.also { reasons += CATEGORY_NAMES.getValue(it) }
        val traits = activity.tags.filter { it.kind == TagKind.TRAIT && it.id in preferences.traitTagIds }.onEach { reasons += it.name }
        val deadline = deadline(activity)
        // 마감 임박은 실제 모집 마감만 본다(활동 시작을 `마감`이라 쓰지 않는다)
        val end = recruitmentEnd(activity)
        val soon = end != null && end > now && Duration.between(now, end) <= DEADLINE_WINDOW
        if (soon) reasons += end.minusNanos(1).atZoneSameInstant(SEOUL).let { "${it.monthValue}월 ${it.dayOfMonth}일 마감" }
        // 대상자 조건이 정리되지 않은 활동은 빼지 않고 알린다(지원 자격 정보가 있을 때만)
        val unchecked = flagUnchecked && !activity.eligibilityChecked
        if (unchecked) reasons += UNCHECKED_REASON
        val points = (fields.maxOfOrNull { it.second } ?: 0) + (if (category != null) CATEGORY_POINTS else 0) + traits.size * TRAIT_POINTS + (if (soon) DEADLINE_POINTS else 0)
        return Scored(listing, points, fields.isNotEmpty() || category != null || traits.isNotEmpty(), unchecked, deadline, reasons)
    }

    // 고른 활동의 프로그램 머리. 활동 수·가장 가까운 마감은 후보(판정을 지난 열린 활동)로 센다
    fun summaries(candidates: List<ActivityListing>, programIds: List<Id>): Map<Id, ProgramSummary> {
        val byProgram = candidates.groupBy { it.program.id }
        return summaries.decorate(programIds.distinct().map { programId ->
            val listings = byProgram.getValue(programId)
            val first = listings.first()
            ProgramSummary(
                id = first.program.id, name = first.program.name, nameEn = first.program.nameEn,
                organizationId = first.organization.id, organizationName = first.organization.name, organizationNameEn = first.organization.nameEn,
                openActivityCount = listings.size, nearestRecruitmentEndAt = listings.mapNotNull { recruitmentEnd(it.activity) }.minOrNull(),
                matchedActivityIds = emptyList(), category = first.program.category,
            )
        }).associateBy { it.id }
    }

    companion object {
        // 점수 → 확인하지 않은 활동은 뒤 → 마감 → 이름 → id
        val ORDER: Comparator<Scored> = compareByDescending<Scored> { it.points }
            .thenBy { it.unchecked }
            .thenBy(nullsLast()) { it.deadline }
            .thenBy { it.listing.activity.name }
            .thenBy { it.listing.activity.id }
        const val UNCHECKED_REASON = "지원 자격 확인 필요"
        const val MAX_POINTS = 100.0 // 대기열에서 벡터·친화도 점수와 더할 때 0~1로 맞추는 기준
        private const val MAX_FIELD_REASONS = 2
        private const val FIELD_POINTS = 40
        private const val PARENT_MATCH_WEIGHT = 0.6
        private const val CURATED_RELEVANCE = 70 // 운영자가 활동에 지정한 분야(연관도 없음)
        private const val PROGRAM_RELEVANCE = 30 // 활동에 분야가 없어 프로그램 분야를 쓴 경우
        private const val CATEGORY_POINTS = 30
        private const val TRAIT_POINTS = 10
        private const val DEADLINE_POINTS = 5
        private val DEADLINE_WINDOW: Duration = Duration.ofDays(14)
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        // 이유 칩에 쓰는 종류 이름(native-v1.md "활동 종류")
        val CATEGORY_NAMES = mapOf(
            ActivityCategory.BOOTCAMP to "부트캠프", ActivityCategory.CONFERENCE to "컨퍼런스", ActivityCategory.WORKSHOP to "워크샵",
            ActivityCategory.STUDY to "스터디", ActivityCategory.CLUB to "동아리", ActivityCategory.LECTURE to "강연",
            ActivityCategory.HACKATHON to "해커톤", ActivityCategory.CONTEST to "공모전", ActivityCategory.MEETUP to "밋업", ActivityCategory.OTHER to "기타",
        )

        // 정렬 키와 같다(native-v1.md "발견 프로그램 묶음"): 모집 마감(시각 또는 날짜의 서울 0시) → 없으면 활동 시작
        fun deadline(activity: Activity): OffsetDateTime? = recruitmentEnd(activity) ?: start(activity.activitySchedule.time)

        // 실제 모집 마감(시각 또는 날짜의 서울 0시)만. 없으면 null(nearestRecruitmentEndAt·`M월 d일 마감` 표기)
        fun recruitmentEnd(activity: Activity): OffsetDateTime? = when (val time = activity.application?.time) {
            is ScheduleTime.Timed -> time.end?.toOffsetDateTime()
            is ScheduleTime.AllDay -> time.endExclusive.atStartOfDay(SEOUL).toOffsetDateTime()
            null -> null
        }

        fun start(time: ScheduleTime?): OffsetDateTime? = when (time) {
            is ScheduleTime.Timed -> time.start?.toOffsetDateTime()
            is ScheduleTime.AllDay -> time.start.atStartOfDay(SEOUL).toOffsetDateTime()
            null -> null
        }

        // 활동 기간의 끝(종일은 다음 날 0시). 모르면 null
        fun end(time: ScheduleTime?): OffsetDateTime? = when (time) {
            is ScheduleTime.Timed -> time.end?.toOffsetDateTime() ?: time.start?.toOffsetDateTime()
            is ScheduleTime.AllDay -> time.endExclusive.atStartOfDay(SEOUL).toOffsetDateTime()
            null -> null
        }
    }
}
