package io.wid.dearby.application.favorite

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.discovery.DiscoveryRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.application.program.ProgramSummaryService
import io.wid.dearby.application.recommendation.RecommendationInputsChanged
import io.wid.dearby.application.recommendation.RecommendationRules
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.discovery.SignalKind
import io.wid.dearby.domain.favorite.FavoriteIds
import io.wid.dearby.domain.favorite.FavoriteTarget
import io.wid.dearby.domain.favorite.NotifyLevel
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 즐겨찾기(native-v1.md "즐겨찾기(하트)·새 모집 푸시 알림"). 로그인 사용자만.
// 활동 하트 = 맞춤 대기열 LIKE(넣으면 LIKE를 남기고 관심 없어요를 지운다, 빼면 LIKE를 지운다). 프로그램 하트는 조직 친화도에 더한다(DiscoveryQueueService)
@Service
class FavoriteService(
    private val favorites: FavoriteRepository,
    private val discovery: DiscoveryRepository,
    private val activities: ActivityRepository,
    private val programs: ProgramRepository,
    private val activityService: ActivityService,
    private val summaries: ProgramSummaryService,
    private val events: ApplicationEventPublisher,
) {

    @Transactional(readOnly = true)
    fun ids(userId: UserId): FavoriteIds {
        val all = favorites.findAll(userId)
        fun of(target: FavoriteTarget) = all.filter { it.target == target }.map { it.targetId }.toSet()
        return FavoriteIds(of(FavoriteTarget.ACTIVITY), of(FavoriteTarget.PROGRAM))
    }

    // 게시 활동·있는 프로그램만(아니면 404). 이미 있으면 level이 있을 때만 알림 수준을 바꾼다
    @Transactional
    fun put(userId: UserId, target: FavoriteTarget, targetId: Id, level: NotifyLevel?) {
        val now = now()
        when (target) {
            FavoriteTarget.ACTIVITY -> {
                activities.findById(targetId) ?: throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $targetId")
                favorites.save(userId, target, targetId, level, now)
                discovery.deleteSignal(userId, targetId, SignalKind.DISMISS)
                // 같은 하트를 다시 보내도 LIKE 시각(감쇠)을 새로 하지 않는다
                if (discovery.signals(userId).none { it.activityId == targetId && it.kind == SignalKind.LIKE }) {
                    discovery.saveSignal(userId, targetId, SignalKind.LIKE, null, now)
                }
            }
            FavoriteTarget.PROGRAM -> {
                if (programs.findAllById(listOf(targetId)).isEmpty()) throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $targetId")
                favorites.save(userId, target, targetId, level, now)
            }
        }
        events.publishEvent(RecommendationInputsChanged(userId))
    }

    // 없어도 그대로(멱등). 활동이면 LIKE를 지운다(UNLIKE)
    @Transactional
    fun delete(userId: UserId, target: FavoriteTarget, targetId: Id) {
        favorites.delete(userId, target, targetId)
        if (target == FavoriteTarget.ACTIVITY) discovery.deleteSignal(userId, targetId, SignalKind.LIKE)
        events.publishEvent(RecommendationInputsChanged(userId))
    }

    // 즐겨찾기 화면에서 그 프로그램을 열면 새 모집 배지를 끈다
    @Transactional
    fun seen(userId: UserId, programId: Id) = favorites.markSeen(userId, programId, now())

    // 프로그램(즐겨찾기한 최근 순) → 활동(마감 임박 순, 지난 활동은 뒤). 지워졌거나 게시되지 않은 대상은 뺀다
    @Transactional(readOnly = true)
    fun list(userId: UserId): FavoritesPage {
        val now = now()
        val all = favorites.findAll(userId)
        val programFavorites = all.filter { it.target == FavoriteTarget.PROGRAM }
        val activityFavorites = all.filter { it.target == FavoriteTarget.ACTIVITY }.associateBy { it.targetId }
        val listings = activityService.publishedListings(ids = activityFavorites.keys)
        val summaryById = summaries.byIds((programFavorites.map { it.targetId } + listings.map { it.program.id }).toSet())
        val fresh = favorites.newRecruitments(listOf(userId), now)[userId].orEmpty()
        val programItems = programFavorites.mapNotNull { f ->
            summaryById[f.targetId]?.let { FavoriteProgram(it, f.createdAt, newRecruitment = f.targetId in fresh, f.notifyLevel) }
        }
        val (past, upcoming) = listings.partition { RecommendationRules.deadline(it.activity)?.isBefore(now) == true }
        val ordered = upcoming.sortedWith(compareBy(nullsLast()) { it: ActivityListing -> RecommendationRules.deadline(it.activity) }) +
            past.sortedByDescending { RecommendationRules.deadline(it.activity) }
        val activityItems = ordered.mapNotNull { listing ->
            val f = activityFavorites.getValue(listing.activity.id)
            summaryById[listing.program.id]?.let { FavoriteActivity(listing, it, f.createdAt, f.notifyLevel) }
        }
        return FavoritesPage(programItems, activityItems)
    }

    private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
}

data class FavoriteProgram(val program: ProgramSummary, val favoritedAt: OffsetDateTime, val newRecruitment: Boolean, val notifyLevel: NotifyLevel)
data class FavoriteActivity(val listing: ActivityListing, val program: ProgramSummary, val favoritedAt: OffsetDateTime, val notifyLevel: NotifyLevel)
data class FavoritesPage(val programs: List<FavoriteProgram>, val activities: List<FavoriteActivity>)
