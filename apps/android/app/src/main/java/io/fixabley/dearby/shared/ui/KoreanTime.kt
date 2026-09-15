package io.fixabley.dearby.shared.ui

import java.time.LocalTime

/** Presentation only; retains nonzero seconds and fractional precision. */
internal fun koreanTime(time: LocalTime): String {
    val hour = when { time.hour == 0 -> "오전 12시"; time.hour < 12 -> "오전 ${time.hour}시"
        time.hour == 12 -> "오후 12시"; else -> "오후 ${time.hour - 12}시" }
    val minute = if (time.minute != 0) " ${time.minute}분" else ""
    val second = if (time.second != 0 || time.nano != 0) {
        val fraction = if (time.nano == 0) "" else "." + time.nano.toString().padStart(9, '0').trimEnd('0')
        " ${time.second}$fraction" + "초"
    } else ""
    return hour + minute + second
}
