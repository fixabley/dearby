package io.wid.dearby.batch.application

import io.wid.dearby.domain.CollectedActivity
import io.wid.dearby.domain.CollectedSchedule
import io.wid.dearby.domain.ParticipationType
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// 모델이 뽑은 활동을 직접 받은 원문과 대조한다(기존 수집기의 원문 검증 규칙).
// 구절이 원문에 있다는 것은 그 구절이 있다는 뜻일 뿐, 모든 필드가 참이라는 증명은 아니다. 그래서 초안으로만 저장한다
object ActivityVerifier {

    private val QUOTE_LENGTH = 10..200

    fun verify(candidate: ExtractedActivity, page: FetchedPage, officialSource: Boolean, checkedAt: OffsetDateTime): Verification {
        if (candidate.roundKey.isBlank() || candidate.name.isBlank()) return Verification.Rejected("회차 식별자나 이름이 없음")
        val quote = candidate.evidenceQuote.trim()
        if (quote.length !in QUOTE_LENGTH) return Verification.Rejected("근거 구절 길이가 ${QUOTE_LENGTH}자 밖")
        if (normalize(quote) !in normalize(page.text)) return Verification.Rejected("근거 구절이 원문에 없음")
        val start = parse(candidate.recruitmentStartAt)
        val end = parse(candidate.recruitmentEndAt)
        if (start != null && end != null && end.isBefore(start)) return Verification.Rejected("모집 끝이 시작보다 앞섬")
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
                participationType = if (candidate.participationType == ParticipationType.SELECTION.name) ParticipationType.SELECTION else ParticipationType.REGISTRATION,
                recruitmentStartAt = start,
                recruitmentEndAt = end,
                dateLabel = candidate.dateLabel.trim(),
                schedules = candidate.schedules.mapNotNull { schedule ->
                    val scheduleStart = parse(schedule.startAt)
                    val scheduleEnd = parse(schedule.endAt)
                    if (scheduleStart != null && scheduleEnd != null && scheduleEnd.isBefore(scheduleStart)) null
                    else CollectedSchedule(schedule.title.trim(), scheduleStart, scheduleEnd, schedule.dateLabel.trim())
                },
                location = candidate.location?.trim()?.ifEmpty { null },
                cost = candidate.cost?.trim()?.ifEmpty { null },
                audience = candidate.audience?.trim()?.ifEmpty { null },
                evidenceQuote = quote,
                checkedAt = checkedAt,
            )
        )
    }

    // 공백·마크다운 기호 차이는 무시한다
    private fun normalize(text: String) = text.replace(Regex("[*_`#>]"), "").replace(Regex("\\s+"), " ").trim()

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
