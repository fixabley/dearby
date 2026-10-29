package io.wid.dearby.application.discovery

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.favorite.FavoriteRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.favorite.FavoriteTarget
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.recommendation.RecommendationInputsChanged
import io.wid.dearby.application.recommendation.RecommendationRepository
import io.wid.dearby.application.recommendation.RecommendationRules
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.TagKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.discovery.ActivityRef
import io.wid.dearby.domain.discovery.Engagement
import io.wid.dearby.domain.discovery.QueueRow
import io.wid.dearby.domain.discovery.Signal
import io.wid.dearby.domain.discovery.SignalKind
import io.wid.dearby.domain.eligibility.EligibilityFilter
import io.wid.dearby.domain.recommendation.RecommendationPreferences
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.pow

// 맞춤 대기열(native-v1.md "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터"). LLM을 쓰지 않고 서버 안에서 규칙 점수 + 취향 벡터 유사도 + 관심 조직 가중치로 고른다.
// 사용자 데이터는 서버 밖으로 나가지 않는다(벡터는 배치가 공개 활동 글로만 만든다)
@Service
class DiscoveryQueueService(
    private val discovery: DiscoveryRepository,
    private val recommendations: RecommendationRepository,
    private val tags: TagRepository,
    private val activityService: ActivityService,
    private val activities: ActivityRepository,
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val favorites: FavoriteRepository,
    private val rules: RecommendationRules,
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // 오늘 대기열. 없으면 만든다. 읽을 때 그사이 마감·조건·내 활동·숨김을 한 번 더 거른다
    @Transactional
    fun queue(userId: UserId): QueuePage {
        val catalog = loadCatalog()
        val user = load(userId, catalog)
        val rows = discovery.queue(userId, today()).ifEmpty { build(user, catalog, keep = emptyList(), exclude = emptySet(), size = SIZE) }
        return page(user, catalog, rows)
    }

    // 더 받기: 오늘 이미 받은 활동을 빼고 남은 후보로 10개를 뒤에 붙인다(남은 후보가 없으면 그대로)
    @Transactional
    fun refill(userId: UserId): QueuePage {
        val catalog = loadCatalog()
        val user = load(userId, catalog)
        val current = discovery.queue(userId, today())
        val added = pick(user, catalog, exclude = current.map { it.activityId }.toSet(), already = emptyList(), count = SIZE)
        val rows = current + added.mapIndexed { i, it -> it.copy(position = (current.maxOfOrNull { r -> r.position } ?: -1) + 1 + i) }
        discovery.replaceQueue(userId, today(), rows)
        return page(user, catalog, rows)
    }

    // POST /v1/me/recommendation-signals. UNLIKE는 LIKE를 지우고, 관심 있어요·관심 없어요는 서로를 지운다.
    // 관심 있어요 = 활동 즐겨찾기(native-v1.md "추천과 하나로"): LIKE는 즐겨찾기에 넣고, UNLIKE·관심 없어요는 뺀다
    @Transactional
    fun signal(userId: UserId, activityId: Id, kind: String, tagId: Id?) {
        if (activities.findById(activityId) == null) throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $activityId")
        if (kind == UNLIKE) {
            discovery.deleteSignal(userId, activityId, SignalKind.LIKE)
            favorites.delete(userId, FavoriteTarget.ACTIVITY, activityId)
        } else {
            val parsed = SignalKind.entries.firstOrNull { it.name == kind && it.fromClient }
                ?: throw InvalidInputException("kind는 ${(SignalKind.entries.filter { it.fromClient }.map { it.name } + UNLIKE).joinToString(", ")} 중 하나입니다")
            val field = if (parsed == SignalKind.LESS_FIELD) {
                tagId?.let { tags.findAllById(listOf(it)).singleOrNull() }?.takeIf { it.kind == TagKind.FIELD }?.id
                    ?: throw InvalidInputException("LESS_FIELD에는 분야 태그 tagId가 필요합니다")
            } else null
            when (parsed) {
                SignalKind.LIKE -> {
                    discovery.deleteSignal(userId, activityId, SignalKind.DISMISS)
                    favorites.save(userId, FavoriteTarget.ACTIVITY, activityId, null, now())
                }
                SignalKind.DISMISS -> {
                    discovery.deleteSignal(userId, activityId, SignalKind.LIKE)
                    favorites.delete(userId, FavoriteTarget.ACTIVITY, activityId)
                }
                else -> Unit
            }
            discovery.saveSignal(userId, activityId, parsed, field, now())
        }
        events.publishEvent(RecommendationInputsChanged(userId))
    }

    // 되돌리기(시트의 고른 뒤 알림): 그 신호를 지운다. 없어도 그대로
    @Transactional
    fun undo(userId: UserId, activityId: Id, kind: String) {
        val parsed = SignalKind.entries.firstOrNull { it.name == kind && it.fromClient } ?: throw InvalidInputException("모르는 kind입니다")
        discovery.deleteSignal(userId, activityId, parsed)
        if (parsed == SignalKind.LIKE) favorites.delete(userId, FavoriteTarget.ACTIVITY, activityId)
        events.publishEvent(RecommendationInputsChanged(userId))
    }

    // 신호·설정·지원 자격·내 활동이 바뀌면 오늘 대기열이 있는 사용자만 다시 만든다. 이미 반응한 카드는 그 자리에 두고 나머지를 새로 고른다
    @EventListener
    fun onInputsChanged(event: RecommendationInputsChanged) {
        val rows = discovery.queue(event.userId, today())
        if (rows.isEmpty()) return
        val catalog = loadCatalog()
        rebuild(load(event.userId, catalog), catalog, rows)
    }

    // 밤 배치(수집 뒤): 대상 사용자마다 오늘 대기열·친화도·취향을 새로 만든다. 사용자 하나가 실패해도 계속한다. 만든 수
    fun buildAll(): Int {
        val catalog = loadCatalog()
        return discovery.queueUsers().count { userId ->
            runCatching {
                transactions.executeWithoutResult { rebuild(load(userId, catalog), catalog, discovery.queue(userId, today())) }
            }.onFailure { log.warn("Discovery queue build failed for a user", it) }.isSuccess
        }
    }

    private fun rebuild(user: UserInputs, catalog: Catalog, rows: List<QueueRow>) {
        val since = startOfToday()
        val keep = rows.filter { user.reactedSince(it.activityId, since) }
        build(user, catalog, keep, exclude = keep.map { it.activityId }.toSet(), size = maxOf(SIZE, rows.size))
    }

    // keep을 앞에 두고 size까지 채워 저장한다
    private fun build(user: UserInputs, catalog: Catalog, keep: List<QueueRow>, exclude: Set<Id>, size: Int): List<QueueRow> {
        val picked = pick(user, catalog, exclude, already = keep.map { it.activityId }, count = size - keep.size)
        val rows = (keep + picked).mapIndexed { i, row -> row.copy(position = i) }
        discovery.saveAffinity(user.userId, user.affinity.organizations)
        discovery.saveTaste(user.userId, catalog.model, user.taste)
        discovery.replaceQueue(user.userId, today(), rows)
        return rows
    }

    // 점수 = min(규칙/100, 1) + 0.25 × 취향 유사도 + 0.15 × 관심 조직 가중치 − 0.5 × 분야 덜 보기. MMR(λ=0.7)로 고르고 프로그램당 2개
    private fun pick(user: UserInputs, catalog: Catalog, exclude: Set<Id>, already: List<Id>, count: Int): List<QueueRow> {
        if (count <= 0) return emptyList()
        val candidates = user.candidates.filter { it.activity.id !in exclude }
        val generatedAt = now()
        val scored = rules.score(candidates, user.preferences, flagUnchecked = user.eligibility != null).map { rule ->
            val activity = rule.listing.activity
            val vector = catalog.vectors[activity.id]
            val similarity = if (vector != null && user.taste != null) Vectors.cosine(user.taste, vector) else 0.0
            val (affinity, affinityReason) = user.affinity.of(rule.listing)
            val penalty = user.lessField.entries.sumOf { (tagId, weight) -> if (activity.fieldTags.any { it.id == tagId || it.parentId == tagId }) weight else 0.0 }
            val total = (rule.points / RecommendationRules.MAX_POINTS).coerceAtMost(1.0) + TASTE_WEIGHT * similarity + AFFINITY_WEIGHT * affinity - LESS_FIELD_WEIGHT * penalty
            val reasons = rule.reasons.filter { it != RecommendationRules.UNCHECKED_REASON } +
                listOfNotNull(vector?.let { user.similarLike(activity.id, it) }, affinityReason) +
                rule.reasons.filter { it == RecommendationRules.UNCHECKED_REASON }
            Candidate(rule.listing, total, vector, reasons)
        }.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.listing.activity.id })

        val chosen = already.mapNotNull { id -> catalog.vectors[id] }.toMutableList()
        val perProgram = already.mapNotNull { id -> catalog.open[id]?.program?.id }.groupingBy { it }.eachCount().toMutableMap()
        val remaining = scored.toMutableList()
        val result = mutableListOf<QueueRow>()
        while (result.size < count) {
            val best = remaining.filter { (perProgram[it.listing.program.id] ?: 0) < MAX_PER_PROGRAM }
                .maxWithOrNull(compareBy<Candidate> { mmr(it, chosen) }.thenByDescending { it.listing.activity.id }) ?: break
            remaining -= best
            best.vector?.let(chosen::add)
            perProgram.merge(best.listing.program.id, 1, Int::plus)
            result += QueueRow(result.size, best.listing.activity.id, best.reasons, generatedAt)
        }
        return result
    }

    private fun mmr(candidate: Candidate, chosen: List<FloatArray>): Double {
        val redundancy = candidate.vector?.let { v -> chosen.maxOfOrNull { Vectors.cosine(v, it) } } ?: 0.0
        return LAMBDA * candidate.score - (1 - LAMBDA) * redundancy
    }

    private fun page(user: UserInputs, catalog: Catalog, rows: List<QueueRow>): QueuePage {
        val byId = user.candidates.associateBy { it.activity.id }
        val live = rows.mapNotNull { row -> byId[row.activityId]?.let { row to it } }
        val programs = rules.summaries(user.candidates, live.map { it.second.program.id })
        val since = startOfToday()
        val items = live.map { (row, listing) ->
            QueueItem(listing, programs.getValue(listing.program.id), row.reasons, liked = user.liked(row.activityId))
        }
        return QueuePage(today(), items, seen = live.count { user.reactedSince(it.first.activityId, since) })
    }

    // 사용자 입력: 설정·지원 자격·신호(가중치·반감기)·후보·취향·친화도
    private fun load(userId: UserId, catalog: Catalog): UserInputs {
        val preferences = recommendations.findPreferences(userId)?.let { saved ->
            val known = tags.findAllById(saved.fieldTagIds + saved.traitTagIds).map { it.id }.toSet()
            saved.copy(fieldTagIds = saved.fieldTagIds.filter { it in known }, traitTagIds = saved.traitTagIds.filter { it in known })
        } ?: RecommendationPreferences.NONE
        val eligibility = recommendations.findEligibility(userId)?.filter(OffsetDateTime.now(SEOUL).year)
        val signals = discovery.signals(userId)
        val implicit = discovery.implicitSignals(userId)
        val now = now()
        fun decay(at: OffsetDateTime) = 0.5.pow(Duration.between(at, now).toHours().coerceAtLeast(0) / (HALF_LIFE_DAYS * 24.0))
        // 활동별 가중치(명시 신호 합 + 내 활동에서 읽은 가장 강한 것)
        val weights = mutableMapOf<Id, Double>()
        signals.filter { it.kind.weight != 0.0 }.forEach { weights.merge(it.activityId, it.kind.weight * decay(it.at), Double::plus) }
        implicit.forEach { weights.merge(it.activityId, it.strength.weight * decay(it.at), Double::plus) }
        val refs = discovery.activityRefs(signals.map { it.activityId } + implicit.map { it.activityId }) // 가중치 없는 숨김 신호도(닫힌 활동이어도) 프로그램을 찾는다
        val vectors = catalog.model?.let { discovery.activityVectors(weights.keys, it) }.orEmpty()

        val mine = implicit.map { it.activityId }.toSet()
        val hiddenPrograms = signals.filter { it.kind == SignalKind.HIDE_PROGRAM }.mapNotNull { refs[it.activityId]?.programId }.toSet()
        val excluded = signals.filter { it.kind == SignalKind.DISMISS || it.kind == SignalKind.INELIGIBLE }.map { it.activityId }.toSet()
        // 지금 참여 중인 활동과 같은 프로그램이고 기간이 겹치는 기수는 뺀다
        val participating = implicit.filter { it.strength == Engagement.PARTICIPATED }.mapNotNull { activities.findById(it.activityId, null) }
        val candidates = catalog.listings.filter { listing ->
            val activity = listing.activity
            (eligibility == null || activity.eligibility?.accepts(eligibility) ?: true) &&
                activity.id !in mine && activity.id !in excluded && listing.program.id !in hiddenPrograms &&
                participating.none { it.programId == listing.program.id && overlaps(it, activity) }
        }

        val taste = Vectors.difference(
            Vectors.weightedMean(weights.filterValues { it > 0 }.mapNotNull { (id, w) -> vectors[id]?.let { it to w } }),
            Vectors.weightedMean(weights.filterValues { it < 0 }.mapNotNull { (id, w) -> vectors[id]?.let { it to -w } }),
        ) ?: catalog.model?.let { model ->
            // 신호가 없으면 추천 설정의 분야·성격 태그 이름 벡터 평균
            Vectors.weightedMean(discovery.tagVectors(preferences.fieldTagIds + preferences.traitTagIds, model).values.map { it to 1.0 })
        }
        val lessField = signals.filter { it.kind == SignalKind.LESS_FIELD && it.tagId != null }.groupBy { it.tagId!! }.mapValues { (_, list) -> list.sumOf { decay(it.at) } }
        val engaged = implicit.groupBy { it.programId }.mapValues { (_, list) -> list.maxOf { it.strength.weight } }
        // 프로그램 하트: 프로그램 신호 +0.6(같은 반감기)
        val programHearts = favorites.findAll(userId).filter { it.target == FavoriteTarget.PROGRAM }.associate { it.targetId to PROGRAM_FAVORITE_WEIGHT * decay(it.createdAt) }
        val programSignals = programs.findAllById(programHearts.keys).associate { it.id to (it.organizationId to programHearts.getValue(it.id)) }
        val affinity = Affinity.of(weights, refs, engaged, organizations, programSignals)
        val likes = signals.filter { it.kind == SignalKind.LIKE }.mapNotNull { s -> vectors[s.activityId]?.let { v -> refs[s.activityId]?.let { it to v } } }
        return UserInputs(userId, preferences, eligibility, candidates, signals, taste, affinity, lessField, likes)
    }

    private fun overlaps(a: io.wid.dearby.domain.Activity, b: io.wid.dearby.domain.Activity): Boolean {
        val aStart = RecommendationRules.start(a.activitySchedule.time) ?: return false
        val bStart = RecommendationRules.start(b.activitySchedule.time) ?: return false
        val aEnd = RecommendationRules.end(a.activitySchedule.time) ?: aStart
        val bEnd = RecommendationRules.end(b.activitySchedule.time) ?: bStart
        return aStart < bEnd && bStart < aEnd
    }

    // 열린 활동 전부와 그 벡터(밤 배치는 한 번 읽어 모든 사용자에게 쓴다)
    private class Catalog(val listings: List<ActivityListing>, val model: String?, val vectors: Map<Id, FloatArray>) {
        val open = listings.associateBy { it.activity.id }
    }

    private fun loadCatalog(): Catalog {
        val listings = activityService.openListings(null)
        val model = discovery.activeModel()
        return Catalog(listings, model, model?.let { discovery.activityVectors(listings.map { it.activity.id }, it) }.orEmpty())
    }

    private class Candidate(val listing: ActivityListing, val score: Double, val vector: FloatArray?, val reasons: List<String>)

    private inner class UserInputs(
        val userId: UserId,
        val preferences: RecommendationPreferences,
        val eligibility: EligibilityFilter?,
        val candidates: List<ActivityListing>,
        private val signals: List<Signal>,
        val taste: FloatArray?,
        val affinity: Affinity,
        val lessField: Map<Id, Double>,
        private val likes: List<Pair<ActivityRef, FloatArray>>,
    ) {
        fun reactedSince(activityId: Id, since: OffsetDateTime) = signals.any { it.activityId == activityId && it.kind.reacted && !it.at.isBefore(since) }
        fun liked(activityId: Id) = signals.any { it.activityId == activityId && it.kind == SignalKind.LIKE }

        // 관심 표시한 활동 중 가장 비슷한 것(같은 활동 제외, 유사도 기준 이상)
        fun similarLike(activityId: Id, vector: FloatArray): String? =
            likes.filter { it.first.activityId != activityId }.map { (ref, v) -> ref to Vectors.cosine(vector, v) }
                .filter { it.second >= SIMILAR_REASON }
                .maxByOrNull { it.second }?.first?.let { "관심 표시한 ${it.name}${Josa.withAnd(it.name)} 비슷해요" }
    }

    private fun today(): LocalDate = LocalDate.now(SEOUL)
    private fun startOfToday(): OffsetDateTime = today().atStartOfDay(SEOUL).toOffsetDateTime()
    private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

    companion object {
        const val SIZE = 10
        const val UNLIKE = "UNLIKE"
        private const val MAX_PER_PROGRAM = 2
        private const val LAMBDA = 0.7
        private const val TASTE_WEIGHT = 0.25
        private const val AFFINITY_WEIGHT = 0.15
        private const val LESS_FIELD_WEIGHT = 0.5
        private const val HALF_LIFE_DAYS = 90.0
        private const val PROGRAM_FAVORITE_WEIGHT = 0.6
        private const val SIMILAR_REASON = 0.75 // 이 유사도 이상일 때만 "관심 표시한 …과 비슷해요"
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

data class QueueItem(val listing: ActivityListing, val program: ProgramSummary, val reasons: List<String>, val liked: Boolean)

// seen: 오늘 반응한 카드 수(앱은 처음 반응하지 않은 카드부터 이어 본다)
data class QueuePage(val date: LocalDate, val items: List<QueueItem>, val seen: Int)
