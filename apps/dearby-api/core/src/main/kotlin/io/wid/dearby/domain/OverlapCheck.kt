package io.wid.dearby.domain

import java.time.LocalTime

// 겹침 확인 시간(서울 시각). 날짜만 있는 일정도 이 시간대로 기기 캘린더와 비교한다(native-v1.md "겹침 확인 시간")
data class OverlapCheck(val startTime: LocalTime, val endTime: LocalTime) {
    init {
        if (!endTime.isAfter(startTime)) throw InvalidInputException("겹침 확인 시간의 끝은 시작보다 뒤여야 합니다")
    }
}
