package io.wid.dearby.batch.application

import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.collection.CollectedActivity
import io.wid.dearby.domain.collection.CollectedSchedule
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// 모델이 뽑은 활동을 직접 받은 원문과 대조한다(기존 수집기의 원문 검증 규칙).
// 구절이 원문에 있다는 것은 그 구절이 있다는 뜻일 뿐, 모든 필드가 참이라는 증명은 아니다. 그래서 초안으로만 저장한다
object ActivityVerifier {

    private val QUOTE_LENGTH = 10..200
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
                    CollectedSchedule(schedule.title.trim(), kind(schedule.kind), scheduleStart, scheduleEnd, schedule.dateLabel.trim(), dayStart, dayEnd)
                },
                location = candidate.location?.trim()?.ifEmpty { null },
                cost = candidate.cost?.trim()?.ifEmpty { null },
                audience = candidate.audience?.trim()?.ifEmpty { null },
                // 모델이 뽑은 대상자 조건을 어휘로 정리한다. 어휘 밖 필드·틀린 모양은 기타(note)로 바꾸고, 쓸 만한 조건이 없으면 null
                eligibility = EligibilityJson.parse(candidate.eligibility, lenient = true),
                evidenceQuote = quote,
                checkedAt = checkedAt,
            )
        )
    }

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
