package io.fixabley.dearby.widgets.notice.noticecard

import io.fixabley.dearby.entities.notice.model.NoticeModel
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class CardScheduleState(val name: String, val dateText: String, val places: List<CardPlaceState>)
internal data class CardPlaceState(val text: String, val mapVenue: NoticeVenue? = null)

internal fun cardSchedules(notice: NoticeModel): List<CardScheduleState> = buildList {
    val application = notice.applicationInformation
    val submission = (application.submissionLocations + listOfNotNull(application.url)).filter { it.isNotBlank() }.distinct()
    add(CardScheduleState("신청 기간", cardPeriodText(application.opensAt, application.opensOn,
        application.closesAt, application.closesOn, application.timezone, application.summary),
        submission.map { CardPlaceState(cardLocationText(it)) }.ifEmpty { listOf(CardPlaceState("신청 위치 미확인")) }))
    notice.schedules.forEach { phase ->
        val places = buildList {
            if (phase.mode == "online" || phase.mode == "hybrid" || !phase.onlineUrl.isNullOrBlank()) {
                add(CardPlaceState(listOfNotNull("온라인", phase.onlineUrl?.takeIf { it.isNotBlank() }?.let(::cardLocationText)).joinToString(" · ")))
            }
            notice.venuesFor(phase).forEach { venue ->
                add(CardPlaceState(listOfNotNull(venue.name, venue.address).filter { it.isNotBlank() }.distinct()
                    .joinToString(" · ").ifEmpty { "장소명 미확인" }, venue.takeIf { it.canOpenMap }))
            }
        }.ifEmpty { listOf(CardPlaceState(if (phase.mode == "offline") "오프라인 · 장소 미확인" else "장소 미확인")) }
        add(CardScheduleState(phase.label, cardPeriodText(phase.startsAt, phase.startsOn, phase.endsAt,
            phase.endsOn, phase.timezone, "일정 미확인"), places))
    }
}

/** Preserve source boundaries and precision; do not fabricate an end or convert date-only values to instants. */
internal fun cardPeriodText(startAt: String?, startOn: String?, endAt: String?, endOn: String?,
    timezone: String, fallback: String): String = try {
    val zone = ZoneId.of(timezone)
    val day = DateTimeFormatter.ofPattern("uuuu.M.d(E)", Locale.KOREAN)
    fun boundary(at: String?, on: String?): String? {
        val date = on?.let { require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(it)); LocalDate.parse(it) }
        val time = at?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) }
        require(time == null || date == null || time.toLocalDate() == date)
        return time?.let { "${it.format(day)} ${if (it.second == 0 && it.nano == 0) it.format(DateTimeFormatter.ofPattern("HH:mm")) else it.toLocalTime()}" }
            ?: date?.let { "${it.format(day)} (시간 미확인)" }
    }
    val start = boundary(startAt, startOn)
    val end = boundary(endAt, endOn)
    val startDate = startAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone).toLocalDate() } ?: startOn?.let(LocalDate::parse)
    val endDate = endAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone).toLocalDate() } ?: endOn?.let(LocalDate::parse)
    require(startDate == null || endDate == null || !endDate.isBefore(startDate))
    require(startAt == null || endAt == null || OffsetDateTime.parse(endAt).toInstant() > OffsetDateTime.parse(startAt).toInstant())
    when {
        start != null && end != null -> "${start}부터 ${end}까지"
        start != null -> "${start}부터 · 종료 미확인"
        end != null -> "시작 미확인 · ${end}까지"
        else -> fallback.ifBlank { "일정 미확인" }
    } + if ((startAt != null || endAt != null) && timezone != "Asia/Seoul") " ($timezone)" else ""
} catch (_: IllegalArgumentException) {
    invalidPeriod(startAt, startOn, endAt, endOn, timezone, fallback)
} catch (_: java.time.DateTimeException) {
    invalidPeriod(startAt, startOn, endAt, endOn, timezone, fallback)
}

private fun invalidPeriod(startAt: String?, startOn: String?, endAt: String?, endOn: String?, zone: String, fallback: String) =
    listOf("날짜 확인 필요", fallback, "시작: ${listOfNotNull(startAt, startOn).joinToString(" / ").ifBlank { "미확인" }}",
        "종료: ${listOfNotNull(endAt, endOn).joinToString(" / ").ifBlank { "미확인" }}", "시간대: $zone").joinToString(" · ")

/** Same host label policy as detailLink, without an upward dependency on Page State. */
private fun cardLocationText(value: String): String = runCatching {
    val uri = URI(value)
    if (uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
        uri.host else value
}.getOrDefault(value)
