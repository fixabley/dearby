package io.wid.dearby.batch.application

import io.wid.dearby.domain.collection.CollectedActivity


// 추출 결과를 원문과 대조한 결과
sealed interface Verification {
    data class Accepted(val activity: CollectedActivity) : Verification
    data class Rejected(val reason: String) : Verification
}
