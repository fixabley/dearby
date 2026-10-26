package io.wid.dearby.application.discovery

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.recommendation.RecommendationRules
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Highlight
import io.wid.dearby.domain.HighlightIcon
import io.wid.dearby.domain.StoryTexts
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 스토리형 쇼츠 한 장의 장면(native-v1.md "맞춤 대기열"). 서버가 내용이 있는 장면만 2~5개 이 순서로 만든다: HOOK → HIGHLIGHTS → ELIGIBILITY → BENEFIT → DEADLINE(늘 마지막)
sealed interface Scene {
    data class Hook(val hook: String, val name: String) : Scene
    data class Highlights(val items: List<Highlight>) : Scene
    // items: 조건 트리로 만든 아이콘 라벨(최대 4개), text: 대상 원문(없으면 라벨을 이은 글)
    data class Eligibility(val text: String, val items: List<EligibilityItem>) : Scene
    data class Benefit(val text: String) : Scene
    // dday: 서울 날짜로 남은 날(0 = 당일, 음수 = 지남), 모르면 null. label: "마감까지" 또는 행사 종류의 "행사까지"
    data class Deadline(val dday: Int?, val label: String, val deadlineText: String?, val reasons: List<String>) : Scene
}

object StoryScenes {

    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val FREE = Regex("무료|전액 지원|전액지원")
    private const val FUNDING_TAG = "funding" // 장학·지원금 성격 태그(native-v1.md "분야·성격 태그와 아이콘")
    private const val MIN_FALLBACK_LINES = 2

    fun of(listing: ActivityListing, reasons: List<String>, today: LocalDate = LocalDate.now(SEOUL)): List<Scene> {
        val activity = listing.activity
        val scenes = mutableListOf<Scene>(Scene.Hook(activity.hook ?: activity.name, activity.name))
        // 핵심 줄이 없으면 비용·기간·장소로 만들되 두 줄 이상일 때만
        val lines = activity.highlights.ifEmpty {
            listOfNotNull(
                activity.cost?.let { Highlight(HighlightIcon.COST, it.take(StoryTexts.MAX_HIGHLIGHT)) },
                activity.activitySchedule.dateLabel.trim().ifEmpty { null }?.let { Highlight(HighlightIcon.PERIOD, it.take(StoryTexts.MAX_HIGHLIGHT)) },
                (activity.activitySchedule.address?.addressLine1 ?: activity.location)?.let { Highlight(HighlightIcon.PLACE, it.take(StoryTexts.MAX_HIGHLIGHT)) },
            ).takeIf { it.size >= MIN_FALLBACK_LINES }.orEmpty()
        }
        if (lines.isNotEmpty()) scenes += Scene.Highlights(lines)
        // 지원 자격을 확인한 활동의 대상(조건 트리 라벨 + 원문)
        if (activity.eligibilityChecked) {
            val items = EligibilityLabels.of(activity.eligibility)
            (activity.audience?.trim()?.ifEmpty { null } ?: items.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.text })
                ?.let { scenes += Scene.Eligibility(it, items) }
        }
        // 무료·전액 지원 비용 문구, 없으면 장학·지원금 태그
        val benefit = activity.cost?.trim()?.takeIf { FREE.containsMatchIn(it) }
            ?: "장학·지원금이 있어요".takeIf { activity.tags.any { it.nameEn == FUNDING_TAG } }
        benefit?.let { scenes += Scene.Benefit(it) }
        // 마감까지(행사 종류는 행사 날짜까지). 행사가 아니면 실제 모집 마감만 쓴다(활동 시작을 마감으로 쓰지 않는다, 2026-10-26)
        val event = activity.category in ActivityCategory.EVENTS
        val at = if (event) RecommendationRules.start(activity.activitySchedule.time) else RecommendationRules.recruitmentEnd(activity)?.minusNanos(1)
        val day = at?.atZoneSameInstant(SEOUL)?.toLocalDate()
        scenes += Scene.Deadline(
            dday = day?.let { ChronoUnit.DAYS.between(today, it).toInt() },
            label = if (event) "행사까지" else "마감까지",
            deadlineText = day?.let { "${it.monthValue}월 ${it.dayOfMonth}일 ${if (event) "행사" else "마감"}" },
            reasons = reasons,
        )
        return scenes
    }
}
