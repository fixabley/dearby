package com.dearby.nativeapp.pages.activity

import com.dearby.nativeapp.entities.activity.model.Place
import com.dearby.nativeapp.shared.ui.TagLabel
import com.dearby.nativeapp.widgets.activity.progress.CalendarDraft
import com.dearby.nativeapp.widgets.activity.progress.ProgressState
import java.time.Instant
import java.time.LocalDate

enum class ActivityListPhase { LOADING, FAILED, LOADED }
enum class ProgramPhase { LOADING, FAILED, LOADED }

/**
 * One activity as shown; recruiting and links are judged once at load time. [status] is the `application` verdict
 * (`모집 중`/`모집 예정`/`모집 마감`/`모집 여부 확인 필요`), [period] the recruitment window text (null when none) and [date] the activity
 * period text. [location] is the venue name (curated place, else the collected phrase); [place] adds address and coordinates.
 * [applyUrl] is the detail CTA link ([applicationUrl], else the official page) while recruitment is not closed, null once it is
 * (contract "신청 확인 조건 완화"): opening it arms the "신청하셨나요?" question.
 */
data class ActivityState(
    val id: String, val title: String, val summary: String, val organization: String, val program: String, val status: String,
    val date: String, val location: String?, val imageUrl: String?, val schedules: List<ScheduleItemState>,
    val officialUrl: String?, val applyUrl: String?, val period: String?, val recruitmentEnd: Instant?, val startAt: String?,
    // The user's own 내 활동 mark (not the organizer's).
    val applied: Boolean = false,
    /** 대상 lines in Korean (contract "대상자 조건"); empty when the activity has no eligibility. */
    val eligibility: List<String> = emptyList(),
    /** 활동 종류 label (부트캠프 …) in display order via [categoryOrder]; null = 미분류, grouped last. */
    val category: String? = null, val categoryOrder: Int = Int.MAX_VALUE,
    /** Discovery order inside a category group: recruitment deadline, else activity start; null last. */
    val sortKey: Instant? = null,
    /** 분야, at most 2 by relevance, and every 분야 for the detail (contract "분야·성격 태그와 아이콘"). */
    val fieldTags: List<TagLabel> = emptyList(), val fieldTagsAll: List<TagLabel> = emptyList(),
    /** 얻을 수 있는 것 (성격) in dictionary order. */
    val traitTags: List<TagLabel> = emptyList(),
    /** The curated venue for the detail `장소` section and the map link (contract "활동 장소"). */
    val place: Place? = null,
    /** The operator's https application link, or null (contract "신청 확인 조건 완화·공개 applicationUrl"). */
    val applicationUrl: String? = null,
    /** At least one schedule can be compared with the device calendar (timed, or date-only with an overlap-check time; contract "겹침 확인 시간"). */
    val overlapCheckable: Boolean = false,
    /** A 내 활동 record whose activity is gone (contract "내 활동 서버 저장"): shown as "삭제된 활동" with only 빼기. */
    val deleted: Boolean = false,
) {
    /** Recruitment still ahead or running: the card emphasises the `모집` row (contract "발견 카드 리뉴얼" 강조 규칙). */
    val recruiting get() = status == "모집 중" || status == "모집 예정"
}
/**
 * One row of the detail's `행사 일정`: 모집 마감, each schedule, then the activity period (the progress stages, contract "내 캘린더에 추가");
 * [draft] is null for an undated one, which gets no `캘린더에 추가` button.
 */
data class ScheduleItemState(val name: String, val dateText: String, val draft: CalendarDraft?,
    /** The block's id and whether it has a 트랙·세션 시간표 (contract "트랙·세션 시간표"): then the row offers `{name} 시간표`. */
    val scheduleId: String? = null, val hasTimetable: Boolean = false)
/**
 * One program header in discovery (contract "발견 프로그램 묶음"); [matchedCount] is how many open activities' names the search hit (shown as `맞는 활동 n개`).
 * [openCount] is 0 for a program with only upcoming or past activities; [upcomingCount] and [nearestStart] describe its 모집 예정
 * activities; [latestActivityAt] is its most recent activity start (contract "마감 숨기기").
 */
data class ProgramState(
    val id: String, val title: String, val organization: String, val openCount: Int, val deadline: Instant?, val matchedCount: Int, val latestActivityAt: Instant? = null,
    val upcomingCount: Int = 0, val nearestStart: Instant? = null,
    /** [deadline] is the activity's own date (no recruitment deadline), so the card says `행사`, not `마감`. */
    val deadlineIsEvent: Boolean = false,
    /** 분야 (at most 2, relevance order) and 성격 of the program (contract "분야·성격 태그와 아이콘"). */
    val fieldTags: List<TagLabel> = emptyList(), val traitTags: List<TagLabel> = emptyList(),
    /** The program logo URL for the card's 44dp logo slot, or null for the initial avatar (contract "프로그램 로고"). */
    val logoUrl: String? = null,
)
/**
 * The open activities of an expanded program, loaded on first expand; [ids] index into [ActivityListState.known]. When none is
 * open, [upcomingIds] are the program's activities whose recruitment (or activity) start is still ahead, nearest first, and
 * [pastIds] those of the last three years, most recent start first (contract "마감 숨기기"). 모집 예정 appears always, 지난 활동 only while
 * 마감 숨기기 is off, and only when nothing is open while it is on.
 */
data class ProgramActivities(
    val phase: ProgramPhase = ProgramPhase.LOADING, val ids: List<String> = emptyList(), val upcomingIds: List<String> = emptyList(), val pastIds: List<String> = emptyList(),
)
/**
 * Discovery results for [query]: [programs] in server order, which are [expanded] (memory only), and their loaded
 * [programActivities]. [known] keeps every loaded activity with its marks so 내 활동 survives a new search.
 * [nextCursor] is null on the last program page; a failed next page sets [moreFailed] only.
 * [askingId]: the activity whose application link was opened, asked about once when the app comes back (contract #148).
 * [progress] is every loaded activity's stage track with the user's marks (contract "내 활동 진행 트랙"), memory only.
 * [openOnly] is the `마감 숨기기` switch (default on, memory only; contract "모집 중인 프로그램만 보기").
 * [refreshing] while a pull to refresh re-reads the first program page (contract "발견 탭 스크롤 동작"); the list stays on screen meanwhile,
 * and when that read fails with a list showing, [refreshFailed] keeps the list and the page shows a passing notice (iOS parity).
 * [mineNotice] is a failed 내 활동 read or write while signed in (contract "내 활동 서버 저장"), shown once; [consentNeeded] when the
 * write was refused for a stale consent (403 CONSENT_REQUIRED), which offers the consent screen instead. [linked] is an activity an App Link
 * asked for, loaded and ready for 발견 to open its detail; [linkNotice] says the link's activity is gone or could not be read (contract
 * "유니버설 링크·App Links·활동 공유·오픈그래프"). Both are consumed once. [mineLoading] while the first read of the account's records after
 * sign-in is under way, so 내 활동 shows its skeleton rather than "신청한 활동이 없어요" (contract "내용 로딩은 스켈레톤"); later re-reads keep the list.
 */
data class ActivityListState(
    val phase: ActivityListPhase = ActivityListPhase.LOADING, val query: String = "", val openOnly: Boolean = true, val programs: List<ProgramState> = emptyList(),
    val expanded: Set<String> = emptySet(), val programActivities: Map<String, ProgramActivities> = emptyMap(),
    val known: Map<String, ActivityState> = emptyMap(), val nextCursor: String? = null, val loadingMore: Boolean = false,
    val moreFailed: Boolean = false, val askingId: String? = null, val progress: Map<String, ProgressState> = emptyMap(), val refreshing: Boolean = false,
    val refreshFailed: Boolean = false, val mineNotice: String? = null, val consentNeeded: Boolean = false,
    val linked: String? = null, val linkNotice: String? = null, val mineLoading: Boolean = false,
) {
    val asking get() = askingId?.let(known::get)
    /** Applied activities, the nearest next stage first; activities without a dated next stage come last. */
    val appliedActivities get() = known.values.filter { it.applied }
        .sortedWith(compareBy({ progress[it.id]?.nextDay == null }, { progress[it.id]?.nextDay ?: LocalDate.MAX }, { it.title }))
    val activeActivities get() = appliedActivities.filter { progress[it.id]?.ended != true }
    val endedActivities get() = appliedActivities.filter { progress[it.id]?.ended == true }
}
