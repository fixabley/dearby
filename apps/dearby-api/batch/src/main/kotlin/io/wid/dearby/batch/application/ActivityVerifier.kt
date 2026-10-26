package io.wid.dearby.batch.application

import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.OverlapCheck
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.Recurrence
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.StoryTexts
import io.wid.dearby.domain.collection.CollectedRecurrence
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.collection.CollectedActivity
import io.wid.dearby.domain.collection.CollectedSchedule
import io.wid.dearby.domain.collection.CollectedTimetable
import io.wid.dearby.domain.timetable.SessionInput
import io.wid.dearby.domain.timetable.SessionKind
import io.wid.dearby.domain.timetable.Speaker
import io.wid.dearby.domain.timetable.TimetableInput
import io.wid.dearby.domain.timetable.TimetableRules
import io.wid.dearby.domain.timetable.TrackInput
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// 모델이 뽑은 활동을 직접 받은 원문과 대조한다(기존 수집기의 원문 검증 규칙).
// 구절이 원문에 있다는 것은 그 구절이 있다는 뜻일 뿐, 모든 필드가 참이라는 증명은 아니다. 그래서 초안으로만 저장한다
object ActivityVerifier {

    private val QUOTE_LENGTH = 10..200
    private val PLACE_QUOTE_LENGTH = 2..200 // 장소 근거 구절("장소: 코엑스")은 짧을 수 있다
        const val MAX_TAGS = 5 // 활동 하나에 붙일 태그 수

    fun verify(candidate: ExtractedActivity, page: FetchedPage, officialSource: Boolean, checkedAt: OffsetDateTime): Verification {
        if (candidate.roundKey.isBlank() || candidate.name.isBlank()) return Verification.Rejected("회차 식별자나 이름이 없음")
        val quote = candidate.evidenceQuote.trim()
        if (quote.length !in QUOTE_LENGTH) return Verification.Rejected("근거 구절 길이가 ${QUOTE_LENGTH}자 밖")
        if (normalize(quote) !in normalize(page.text)) return Verification.Rejected("근거 구절이 원문에 없음")
        val start = parse(candidate.recruitmentStartAt)
        val end = parse(candidate.recruitmentEndAt)
        if (start != null && end != null && end.isBefore(start)) return Verification.Rejected("모집 끝이 시작보다 앞섬")
        // 날짜만 있는 기간은 종일 일정이 된다. 시각이 있으면 날짜는 쓰지 않고, 끝이 시작보다 앞서면 날짜를 버린다(dateLabel만 남는다)
        val (recruitmentStartDate, recruitmentEndDate) = if (start == null && end == null) dates(candidate.recruitmentStartDate, candidate.recruitmentEndDate) else null to null
        val (startDate, endDate) = dates(candidate.startDate, candidate.endDate)
        return Verification.Accepted(
            CollectedActivity(
                roundKey = candidate.roundKey.trim(),
                name = candidate.name.trim(),
                nameEn = candidate.nameEn?.trim()?.ifEmpty { null },
                description = candidate.description.trim(),
                descriptionEn = candidate.descriptionEn?.trim()?.ifEmpty { null },
                officialUrl = page.url,
                officialSource = officialSource,
                // 신청 주소는 원문 페이지에 실제 링크로 있을 때만 쓴다
                applicationUrl = candidate.applicationUrl?.trim()?.takeIf { it.startsWith("https://") && it in page.links },
                participationType = ParticipationType.entries.firstOrNull { it.name == candidate.participationType }, // 본문에 없으면 null(저장할 때 이전 기수에서 가져온다)
                category = ActivityCategory.entries.firstOrNull { it.name == candidate.category }, // 모르는 코드는 미분류
                tags = candidate.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_TAGS), // 사전 대조는 저장할 때
                fieldTags = candidate.fieldTags.filter { it.name.isNotBlank() }.take(MAX_TAGS).associate { it.name.trim() to it.relevance?.coerceIn(0, 100) },
                recruitmentStartAt = start,
                recruitmentEndAt = end,
                recruitmentStartDate = recruitmentStartDate,
                recruitmentEndDate = recruitmentEndDate,
                dateLabel = candidate.dateLabel.trim(),
                startDate = startDate,
                endDate = endDate,
                schedules = candidate.schedules.mapNotNull { schedule ->
                    val scheduleStart = parse(schedule.startAt)
                    val scheduleEnd = parse(schedule.endAt)
                    if (scheduleStart != null && scheduleEnd != null && scheduleEnd.isBefore(scheduleStart)) return@mapNotNull null
                    val (dayStart, dayEnd) = if (scheduleStart == null && scheduleEnd == null) dates(schedule.startDate, schedule.endDate) else null to null
                    // 반복은 시각 일정에만 둔다(수집기는 겹침 확인 시간을 만들지 않는다). 틀린 규칙은 그 필드만 버린다
                    val rule = scheduleStart?.let { recurrence(schedule.recurrence, ScheduleTime.Timed(it.toZonedDateTime(), scheduleEnd?.toZonedDateTime()), null) }
                    val time = when {
                        scheduleStart != null || scheduleEnd != null -> ScheduleTime.Timed(scheduleStart?.toZonedDateTime(), scheduleEnd?.toZonedDateTime())
                        dayStart != null || dayEnd != null -> ScheduleTime.AllDay(dayStart ?: dayEnd!!, (dayEnd ?: dayStart!!).plusDays(1))
                        else -> null
                    }
                    val timetable = schedule.timetable?.let { timetable(it, time, rule != null, page) }
                    CollectedSchedule(schedule.title.trim(), kind(schedule.kind), scheduleStart, scheduleEnd, schedule.dateLabel.trim(), dayStart, dayEnd, rule, timetable)
                },
                location = candidate.location?.trim()?.ifEmpty { null },
                cost = candidate.cost?.trim()?.ifEmpty { null },
                // 대기열 장면 글(native-v1.md "맞춤 대기열"): 길거나 모르는 icon은 그 항목만 버린다
                hook = StoryTexts.collectedHook(candidate.hook),
                highlights = StoryTexts.collectedHighlights(candidate.highlights.map { it.icon to it.text }),
                audience = candidate.audience?.trim()?.ifEmpty { null },
                // 모델이 뽑은 대상자 조건을 어휘로 정리한다. 어휘 밖 필드·틀린 모양은 기타(note)로 바꾸고, 쓸 만한 조건이 없으면 null
                eligibility = EligibilityJson.parse(candidate.eligibility, lenient = true),
                evidenceQuote = quote,
                checkedAt = checkedAt,
                activityRecurrence = candidate.activityRecurrence?.let { extracted ->
                    val time = runCatching { OverlapCheck(LocalTime.parse(extracted.startTime.trim()), LocalTime.parse(extracted.endTime.trim())) }.getOrNull()
                    val rule = startDate?.let { recurrence(extracted.rule, ScheduleTime.AllDay(it, (endDate ?: it).plusDays(1)), time) }
                    if (rule != null && time != null) CollectedRecurrence(rule, time) else null
                },
            )
        )
    }

    // 장소(native-v1.md "수집기 장소"): 온라인이 아니고, 근거 구절이 원문에 있고 그 안에 장소 이름이 있을 때만 검색어로 쓴다.
    // 주소는 원문에 있을 때만 함께 쓴다. 아니면 null이고 활동은 그대로 저장된다(장소만 버린다)
    fun place(candidate: ExtractedActivity, page: FetchedPage): PlaceQuery? {
        val place = candidate.place?.takeUnless { it.online } ?: return null
        val name = place.name?.trim()?.ifEmpty { null } ?: return null
        val quote = place.evidenceQuote?.trim()?.takeIf { it.length in PLACE_QUOTE_LENGTH } ?: return null
        val text = normalize(page.text)
        if (normalize(quote) !in text || compact(name) !in compact(quote)) return null
        val address = place.address?.trim()?.ifEmpty { null }?.takeIf { compact(it) in compact(page.text) }
        return PlaceQuery(name, address)
    }


    private fun compact(text: String) = normalize(text).replace(" ", "").lowercase()

    // 하위 시간표(native-v1.md "트랙·세션 시간표"): 블록 시간 안·같은 트랙 겹침 없음(어드민과 같은 규칙)이고 세션 제목이 모두 원문에 있을 때만.
    // 어긋나면 시간표만 버리고 일정·활동은 저장한다. 빈 시간표는 없는 것으로 본다
    private fun timetable(extracted: ExtractedTimetable, time: ScheduleTime?, recurring: Boolean, page: FetchedPage): CollectedTimetable? {
        if (extracted.sessions.isEmpty()) return null
        val text = compact(page.text)
        if (extracted.sessions.any { compact(it.title) !in text }) return null
        val trackNames = extracted.tracks.map { it.name.trim() }
        val sessions = extracted.sessions.map { session ->
            val start = parse(session.startAt) ?: return null
            val end = parse(session.endAt) ?: return null
            val track = session.track?.trim()?.ifEmpty { null }?.let { name -> trackNames.indexOf(name).takeIf { it >= 0 } ?: return null }
            SessionInput(
                id = null,
                track = track,
                kind = SessionKind.entries.firstOrNull { it.name.equals(session.kind?.trim(), ignoreCase = true) } ?: SessionKind.TALK,
                title = session.title,
                description = session.description,
                startAt = start,
                endAt = end,
                speakers = session.speakers.filter { it.name.isNotBlank() }.map { Speaker(it.name, it.affiliation) },
                tagIds = emptyList(),
            )
        }
        val input = TimetableInput(extracted.tracks.map { TrackInput(null, it.name, it.room) }, sessions)
        val validated = runCatching { TimetableRules.validate(input, TimetableRules.window(time, recurring)) }.getOrNull() ?: return null
        return CollectedTimetable(validated, extracted.sessions.map { s -> s.tags.map { it.trim() }.filter { it.isNotEmpty() } })
    }

    // 규칙이 허용 부분집합이고 일정과 맞을 때만(native-v1.md "반복 일정과 겹침 결과 묶음"). 아니면 null
    private fun recurrence(text: String?, time: ScheduleTime, overlapCheck: OverlapCheck?): Recurrence? =
        Recurrence.parseOrNull(text)?.takeIf { runCatching { it.requireFits(time, overlapCheck) }.isSuccess }

    // 모델이 준 일정 종류. 모르거나 어긋나면 행사(EVENT)로 본다
    private fun kind(value: String?): ScheduleKind = ScheduleKind.entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: ScheduleKind.EVENT

    // 공백·마크다운 기호 차이는 무시한다
    private fun normalize(text: String) = text.replace(Regex("[*_`#>]"), "").replace(Regex("\\s+"), " ").trim()

    // 날짜만 있는 기간. 읽을 수 없는 날짜는 없는 것으로, 끝이 시작보다 앞서면 둘 다 버린다
    private fun dates(startValue: String?, endValue: String?): Pair<LocalDate?, LocalDate?> {
        val start = parseDate(startValue)
        val end = parseDate(endValue)
        return if (start != null && end != null && end.isBefore(start)) null to null else start to end
    }

    private fun parseDate(value: String?): LocalDate? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    // 읽을 수 없는 시각은 없는 것으로 본다(날짜만 있는 공지는 dateLabel에 남는다)
    private fun parse(value: String?): OffsetDateTime? =
        value?.takeIf { it.isNotBlank() }?.let {
            try {
                OffsetDateTime.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }
}
