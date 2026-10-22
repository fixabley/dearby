package io.wid.dearby.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// 반복 일정(native-v1.md "반복 일정과 겹침 결과 묶음"): RFC 5545 RRULE 본문의 허용 부분집합. rule은 정규화된 값(FREQ;INTERVAL;BYDAY;UNTIL|COUNT)
// 저장된 값은 parse를 지난 것이라 그대로 감싼다
@JvmInline
value class Recurrence(val rule: String) {

    // UNTIL의 서울 날짜(포함). COUNT로 끝나면 null
    val untilDate: LocalDate?
        get() = parts()["UNTIL"]?.let(::untilToDate)

    // 반복은 날짜가 있는 일정에만 둔다. 시각 일정은 시작이 첫 회차, 날짜만 있는 일정은 겹침 확인 시간이 매 회차 시간이다. UNTIL은 첫 날 이후
    fun requireFits(time: ScheduleTime?, overlapCheck: OverlapCheck?) {
        val firstDay = when (time) {
            is ScheduleTime.Timed -> time.start?.withZoneSameInstant(SEOUL)?.toLocalDate() ?: throw InvalidInputException("반복하려면 시작 시각이 있어야 합니다")
            is ScheduleTime.AllDay -> time.start.also { if (overlapCheck == null) throw InvalidInputException("날짜만 있는 일정을 반복하려면 겹침 확인 시간이 있어야 합니다") }
            null -> throw InvalidInputException("반복은 날짜가 있는 일정에만 둘 수 있습니다")
        }
        untilDate?.let { if (it.isBefore(firstDay)) throw InvalidInputException("반복 끝(UNTIL)은 첫 날 이후여야 합니다") }
    }

    private fun parts(): Map<String, String> = rule.split(';').associate { it.substringBefore('=') to it.substringAfter('=') }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
        private val DAYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))

        // 입력 검증·정규화. 틀리면 400. 대소문자와 앞의 "RRULE:"은 받아 준다
        fun parse(text: String): Recurrence {
            val parts = text.trim().uppercase().removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.map {
                val (key, value) = it.split('=').takeIf { pair -> pair.size == 2 && pair[1].isNotEmpty() } ?: invalid("'$it'")
                key to value
            }
            if (parts.map { it.first }.toSet().size != parts.size) invalid("같은 키가 두 번 있습니다")
            val map = parts.toMap()
            (map.keys - setOf("FREQ", "INTERVAL", "BYDAY", "UNTIL", "COUNT")).firstOrNull()?.let { invalid("$it 는 쓸 수 없습니다") }
            val freq = map["FREQ"]?.takeIf { it == "DAILY" || it == "WEEKLY" } ?: invalid("FREQ는 DAILY 또는 WEEKLY입니다")
            val interval = map["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n in 1..4 } ?: invalid("INTERVAL은 1~4입니다") }
            val days = map["BYDAY"]?.split(',')?.map { it.takeIf { d -> d in DAYS } ?: invalid("BYDAY는 MO..SU 목록입니다") }?.toSet()
                ?.sortedBy(DAYS::indexOf)
            if ((map["UNTIL"] == null) == (map["COUNT"] == null)) invalid("UNTIL과 COUNT 중 하나만 있어야 합니다")
            val end = map["UNTIL"]?.let { untilToDate(it); "UNTIL=$it" }
                ?: "COUNT=" + (map["COUNT"]!!.toIntOrNull()?.takeIf { it in 1..400 } ?: invalid("COUNT는 1~400입니다"))
            return Recurrence(
                listOfNotNull("FREQ=$freq", interval?.takeIf { it != 1 }?.let { "INTERVAL=$it" }, days?.let { "BYDAY=${it.joinToString(",")}" }, end).joinToString(";")
            )
        }

        // 수집기: 틀린 값은 그 필드만 버린다
        fun parseOrNull(text: String?): Recurrence? = text?.takeIf { it.isNotBlank() }?.let { runCatching { parse(it) }.getOrNull() }

        private fun untilToDate(value: String): LocalDate = try {
            if (value.length == 8) LocalDate.parse(value, DATE) else ZonedDateTime.parse(value, DATE_TIME).withZoneSameInstant(SEOUL).toLocalDate()
        } catch (_: DateTimeParseException) {
            invalid("UNTIL은 yyyyMMdd 또는 yyyyMMddTHHmmssZ입니다")
        }

        private fun invalid(reason: String): Nothing = throw InvalidInputException("반복 규칙이 올바르지 않습니다: $reason")
    }
}
