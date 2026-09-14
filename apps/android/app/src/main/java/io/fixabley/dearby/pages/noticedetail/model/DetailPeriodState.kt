package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.shared.ui.compactPeriodText
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class DetailPeriodLine(val date: String, val time: String)
internal data class DetailPeriodState(val lines: List<DetailPeriodLine>, val timezone: String, val valid: Boolean)

/** Reuses the strict card validation but produces independent calendar-detail rows. */
internal fun detailPeriod(startAt: String?, startOn: String?, endAt: String?, endOn: String?,
    timezone: String, fallback: String, endLabel: String = "종료"): DetailPeriodState {
    val invalid = "\u0000"
    if (compactPeriodText(startAt, startOn, endAt, endOn, timezone, invalid) == invalid) {
        val fields = listOfNotNull(startAt?.let { "시작 시각: $it" }, startOn?.let { "시작 날짜: $it" },
            endAt?.let { "$endLabel 시각: $it" }, endOn?.let { "$endLabel 날짜: $it" })
        return DetailPeriodState(listOf(DetailPeriodLine(fallback, fields.joinToString("\n"))), timezone, false)
    }
    val zone = ZoneId.of(timezone)
    val s = startAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) }
    val e = endAt?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone) }
    val sd = s?.toLocalDate() ?: startOn?.let(LocalDate::parse)
    val ed = e?.toLocalDate() ?: endOn?.let(LocalDate::parse)
    val day = DateTimeFormatter.ofPattern("uuuu년 M월 d일 (E)", Locale.KOREAN)
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
    fun time(value: java.time.ZonedDateTime?): String = value?.let {
        if (it.second != 0 || it.nano != 0) it.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)
        else it.format(clock)
    } ?: "시간 미확인"
    val st = time(s)
    val et = time(e)
    val lines = when {
        sd != null && sd == ed -> listOf(DetailPeriodLine(sd.format(day),
            when {
                s == null && e == null -> "시간 미확인"
                s == null || e == null -> "시작 $st · $endLabel $et"
                else -> "$st – $et"
            }))
        else -> buildList {
            if (sd == null) add(DetailPeriodLine("시작 미확인", ""))
            if (sd != null) add(DetailPeriodLine("시작 · ${sd.format(day)}", st))
            if (ed != null) add(DetailPeriodLine("$endLabel · ${ed.format(day)}", et))
            if (ed == null) add(DetailPeriodLine("$endLabel 미확인", ""))
        }
    }
    return DetailPeriodState(lines, if (timezone == "Asia/Seoul") "한국 시간" else timezone, true)
}

/** Only suppress an exactly matching, ordinary closing timestamp; uncertainty/24:00 stays visible. */
internal fun applicationPeriodNote(summary: String, closesAt: String?, details: DetailPeriodState): String? {
    val exact = closesAt?.take(16)?.replace('T', ' ')?.plus(" 마감")
    return summary.takeUnless { !details.valid || it == exact }
}
