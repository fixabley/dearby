package io.wid.dearby.application.recommendation

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.profile.ProfileService
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.TagKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.eligibility.EligibilityField
import io.wid.dearby.domain.recommendation.EligibilityProfile
import io.wid.dearby.domain.recommendation.RecommendationPreferences
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 추천 설정·지원 자격 정보·추천(native-v1.md "추천 설정 퍼널·지원 자격 정보·추천"). 1단계는 규칙 점수다
@Service
class RecommendationService(
    private val recommendations: RecommendationRepository,
    private val tags: TagRepository,
    private val activities: ActivityService,
    private val rules: RecommendationRules,
    private val events: ApplicationEventPublisher,
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
        events.publishEvent(RecommendationInputsChanged(userId))
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
        events.publishEvent(RecommendationInputsChanged(userId))
        return profile
    }

    @Transactional
    fun deleteEligibility(userId: UserId) {
        recommendations.deleteEligibility(userId)
        events.publishEvent(RecommendationInputsChanged(userId))
    }

    // 저장된 설정(없으면 빈 설정)과 지원 자격 정보로. 로그인만 받는다(native-v1.md "비로그인에서 만들기·추천을 막음")
    @Transactional(readOnly = true)
    fun recommend(userId: UserId, size: Int): RecommendationPage {
        if (size !in 1..MAX_SIZE) throw InvalidInputException("size는 1에서 ${MAX_SIZE} 사이여야 합니다")
        val preferences = recommendations.findPreferences(userId) ?: RecommendationPreferences.NONE
        val filter = recommendations.findEligibility(userId)?.filter(currentYear())
        // 열린 활동 + 지원 자격 정보가 있으면 대상자 조건 1·2차 판정으로 맞지 않는 활동을 뺀다
        val candidates = activities.openListings(filter)
        val scored = rules.score(candidates, preferences, flagUnchecked = filter != null)
            .filter { preferences.isEmpty || it.matched }
            .sortedWith(RecommendationRules.ORDER)
        // 같은 프로그램의 활동은 최대 2개
        val perProgram = mutableMapOf<Long, Int>()
        val picked = scored.filter { perProgram.merge(it.listing.program.id, 1, Int::plus)!! <= MAX_PER_PROGRAM }.take(size)
        val programs = rules.summaries(candidates, picked.map { it.listing.program.id })
        return RecommendationPage(picked.map { Recommendation(it.listing, programs.getValue(it.listing.program.id), it.reasons) }, scored.size)
    }

    private fun currentYear() = OffsetDateTime.now(SEOUL).year

    companion object {
        const val DEFAULT_SIZE = 10
        const val MAX_SIZE = 50
        private const val MAX_CHOICES = 50
        private const val MAX_PER_PROGRAM = 2
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

// 추천 입력(설정·지원 자격 정보·내 활동·대기열 신호)이 바뀌었다. 오늘 대기열이 있으면 그 사용자만 다시 만든다(DiscoveryQueueService)
data class RecommendationInputsChanged(val userId: UserId)

// 추천 한 항목. reasons는 사람이 읽는 짧은 이유(분야·종류·성격·마감, 지원 자격 확인 필요)
data class Recommendation(val listing: ActivityListing, val program: ProgramSummary, val reasons: List<String>)

// total: 판정·설정 일치를 지난 추천 후보 수(프로그램당 2개·size로 자르기 전). 퍼널 결과의 "n개"
data class RecommendationPage(val items: List<Recommendation>, val total: Int)
