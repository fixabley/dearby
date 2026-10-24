package io.wid.dearby.application.recommendation

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.profile.ProfileService
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.program.ProgramSummaryService
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.Activity
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.TagKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.eligibility.EligibilityField
import io.wid.dearby.domain.recommendation.EligibilityProfile
import io.wid.dearby.domain.recommendation.RecommendationPreferences
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

// 추천 설정·지원 자격 정보·추천(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"). 1단계는 규칙 점수다
@Service
class RecommendationService(
    private val recommendations: RecommendationRepository,
    private val tags: TagRepository,
    private val activities: ActivityService,
    private val summaries: ProgramSummaryService,
) {

    // 저장한 적이 없으면 빈 설정(completedAt null). 지워진 태그 id는 뺀다
    @Transactional(readOnly = true)
    fun preferences(userId: UserId): RecommendationPreferences {
        val saved = recommendations.findPreferences(userId) ?: return RecommendationPreferences.NONE
        val known = tags.findAllById(saved.fieldTagIds + saved.traitTagIds).map { it.id }.toSet()
        return saved.copy(fieldTagIds = saved.fieldTagIds.filter { it in known }, traitTagIds = saved.traitTagIds.filter { it in known })
    }

    // 분야는 분야 태그, 얻고 싶은 것은 성격 태그여야 한다(아니면 400). 저장하면 완료로 본다
    @Transactional
    fun savePreferences(userId: UserId, input: RecommendationPreferences): RecommendationPreferences {
        val fieldIds = input.fieldTagIds.distinct()
        val traitIds = input.traitTagIds.distinct()
        if (fieldIds.size > MAX_CHOICES || traitIds.size > MAX_CHOICES) throw InvalidInputException("태그는 ${MAX_CHOICES}개까지 고를 수 있습니다")
        val kinds = tags.findAllById(fieldIds + traitIds).associate { it.id to it.kind }
        if (fieldIds.any { kinds[it] != TagKind.FIELD }) throw InvalidInputException("fieldTagIds에는 분야 태그 id만 넣을 수 있습니다")
        if (traitIds.any { kinds[it] != TagKind.TRAIT }) throw InvalidInputException("traitTagIds에는 성격 태그 id만 넣을 수 있습니다")
        recommendations.savePreferences(userId, RecommendationPreferences(fieldIds, input.categories.distinct(), traitIds))
        return preferences(userId)
    }

    @Transactional(readOnly = true)
    fun eligibility(userId: UserId): EligibilityProfile? = recommendations.findEligibility(userId)

    // 현재 동의 버전이 아니면 400 CONSENT_VERSION. 값은 오류 문장·로그에 넣지 않는다
    @Transactional
    fun saveEligibility(userId: UserId, consentVersion: String, educationStatus: String?, employment: String?, schoolYear: Int?, birthYear: Int?): EligibilityProfile {
        if (consentVersion != EligibilityProfile.CONSENT_VERSION) {
            throw InvalidInputException("지원 자격 정보 수집·이용 동의 버전이 현재 버전과 다릅니다", code = ProfileService.CONSENT_VERSION)
        }
        fun vocabulary(field: EligibilityField, value: String?): String? = value?.trim()?.ifEmpty { null }?.let { given ->
            field.valueVocabulary.firstOrNull { it.second.equals(given, ignoreCase = true) }?.second
                ?: throw InvalidInputException("${field.en}은(는) ${field.valueVocabulary.joinToString(", ") { it.second }} 중 하나입니다")
        }
        val status = vocabulary(EligibilityField.EDUCATION_STATUS, educationStatus)
        val job = vocabulary(EligibilityField.EMPLOYMENT, employment)
        if (schoolYear != null && schoolYear !in 1..EligibilityProfile.MAX_SCHOOL_YEAR) throw InvalidInputException("schoolYear는 1에서 ${EligibilityProfile.MAX_SCHOOL_YEAR} 사이입니다")
        if (birthYear != null && birthYear !in EligibilityProfile.MIN_BIRTH_YEAR..currentYear()) throw InvalidInputException("birthYear가 올바르지 않습니다")
        // 같은 버전에 다시 동의한 것이면 처음 동의한 시각을 지킨다
        val consentedAt = recommendations.findEligibility(userId)?.takeIf { it.consentVersion == consentVersion }?.consentedAt ?: OffsetDateTime.now(ZoneOffset.UTC)
        val profile = EligibilityProfile(status, job, schoolYear, birthYear, consentVersion, consentedAt)
        recommendations.saveEligibility(userId, profile)
        return profile
    }

    @Transactional
    fun deleteEligibility(userId: UserId) = recommendations.deleteEligibility(userId)

    // 로그인이면 저장된 설정(없으면 쿼리 값)과 지원 자격 정보, 비로그인이면 쿼리 값만
    @Transactional(readOnly = true)
    fun recommend(userId: UserId?, query: RecommendationPreferences, size: Int): RecommendationPage {
        if (size !in 1..MAX_SIZE) throw InvalidInputException("size는 1에서 ${MAX_SIZE} 사이여야 합니다")
        val preferences = userId?.let(recommendations::findPreferences) ?: query
        val filter = userId?.let(recommendations::findEligibility)?.filter(currentYear())
        // 열린 활동 + 지원 자격 정보가 있으면 대상자 조건 1·2차 판정으로 맞지 않는 활동을 뺀다
        val candidates = activities.openListings(filter)
        val relevance = recommendations.fieldRelevance(candidates.map { it.activity.id })
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val scored = candidates.mapNotNull { listing ->
            score(listing, preferences, relevance[listing.activity.id].orEmpty(), now, flagUnchecked = filter != null)
                .takeIf { preferences.isEmpty || it.matched }
        }.sortedWith(
            compareByDescending<Scored> { it.points }
                .thenBy { it.unchecked }
                .thenBy(nullsLast()) { it.deadline }
                .thenBy { it.listing.activity.name }
                .thenBy { it.listing.activity.id }
        )
        // 같은 프로그램의 활동은 최대 2개
        val perProgram = mutableMapOf<Long, Int>()
        val picked = scored.filter { perProgram.merge(it.listing.program.id, 1, Int::plus)!! <= MAX_PER_PROGRAM }.take(size)
        // 프로그램 머리는 추천 후보(판정을 지난 열린 활동)로 센다
        val byProgram = candidates.groupBy { it.program.id }
        val programs = summaries.decorate(picked.map { it.listing.program.id }.distinct().map { programId ->
            val listings = byProgram.getValue(programId)
            val first = listings.first()
            ProgramSummary(
                id = first.program.id, name = first.program.name, nameEn = first.program.nameEn,
                organizationId = first.organization.id, organizationName = first.organization.name, organizationNameEn = first.organization.nameEn,
                openActivityCount = listings.size, nearestRecruitmentEndAt = listings.mapNotNull { deadline(it.activity) }.minOrNull(),
                matchedActivityIds = emptyList(), category = first.program.category,
            )
        }).associateBy { it.id }
        return RecommendationPage(picked.map { Recommendation(it.listing, programs.getValue(it.listing.program.id), it.reasons) }, scored.size)
    }

    private class Scored(val listing: ActivityListing, val points: Int, val matched: Boolean, val unchecked: Boolean, val deadline: OffsetDateTime?, val reasons: List<String>)

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
        val soon = deadline != null && deadline > now && Duration.between(now, deadline) <= DEADLINE_WINDOW
        if (soon) reasons += deadline!!.minusNanos(1).atZoneSameInstant(SEOUL).let { "${it.monthValue}월 ${it.dayOfMonth}일 마감" }
        // 대상자 조건이 정리되지 않은 활동은 빼지 않고 알린다(지원 자격 정보가 있을 때만)
        val unchecked = flagUnchecked && !activity.eligibilityChecked
        if (unchecked) reasons += "지원 자격 확인 필요"
        val points = (fields.maxOfOrNull { it.second } ?: 0) + (if (category != null) CATEGORY_POINTS else 0) + traits.size * TRAIT_POINTS + (if (soon) DEADLINE_POINTS else 0)
        return Scored(listing, points, fields.isNotEmpty() || category != null || traits.isNotEmpty(), unchecked, deadline, reasons)
    }

    // 정렬 키와 같다(native-v1.md "발견 프로그램 묶음"): 모집 마감(시각 또는 날짜의 서울 0시) → 없으면 활동 시작
    private fun deadline(activity: Activity): OffsetDateTime? {
        fun start(time: ScheduleTime?) = when (time) {
            is ScheduleTime.Timed -> time.start?.toOffsetDateTime()
            is ScheduleTime.AllDay -> time.start.atStartOfDay(SEOUL).toOffsetDateTime()
            null -> null
        }
        val end = when (val time = activity.application?.time) {
            is ScheduleTime.Timed -> time.end?.toOffsetDateTime()
            is ScheduleTime.AllDay -> time.endExclusive.atStartOfDay(SEOUL).toOffsetDateTime()
            null -> null
        }
        return end ?: start(activity.activitySchedule.time)
    }

    private fun currentYear() = OffsetDateTime.now(SEOUL).year

    companion object {
        const val DEFAULT_SIZE = 10
        const val MAX_SIZE = 50
        private const val MAX_CHOICES = 50
        private const val MAX_PER_PROGRAM = 2
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
        private val CATEGORY_NAMES = mapOf(
            ActivityCategory.BOOTCAMP to "부트캠프", ActivityCategory.CONFERENCE to "컨퍼런스", ActivityCategory.WORKSHOP to "워크샵",
            ActivityCategory.STUDY to "스터디", ActivityCategory.CLUB to "동아리", ActivityCategory.LECTURE to "강연",
            ActivityCategory.HACKATHON to "해커톤", ActivityCategory.CONTEST to "공모전", ActivityCategory.MEETUP to "밋업", ActivityCategory.OTHER to "기타",
        )
    }
}

// 추천 한 항목. reasons는 사람이 읽는 짧은 이유(분야·종류·성격·마감, 지원 자격 확인 필요)
data class Recommendation(val listing: ActivityListing, val program: ProgramSummary, val reasons: List<String>)

// total: 판정·설정 일치를 지난 추천 후보 수(프로그램당 2개·size로 자르기 전). 퍼널 결과의 "n개"
data class RecommendationPage(val items: List<Recommendation>, val total: Int)
