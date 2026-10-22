package io.wid.dearby.admin.application

import io.wid.dearby.domain.OverlapCheck

// 모집·활동 기간 객체 안의 overlapCheck(native-v1.md "겹침 확인 시간"). 이 값이 있으면 보낸 것이고, overlapCheck가 null이면 지운다
data class OverlapChange(val overlapCheck: OverlapCheck?)
