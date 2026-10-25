package io.wid.dearby.domain.favorite

import io.wid.dearby.domain.Id
import java.time.OffsetDateTime

// 즐겨찾기(native-v1.md "즐겨찾기(하트)·새 모집 푸시 알림"). 대상은 활동 또는 프로그램
enum class FavoriteTarget { ACTIVITY, PROGRAM }

// 알림 수준(native-v1.md "프로그램·활동별 알림 수준"): 전체(모든 일정 + 신규) · 신청일만(모집 시작·마감 + 신규) · 없음. 하트를 누를 때 기본은 신청일만
enum class NotifyLevel { ALL, APPLICATION, NONE }

data class Favorite(val target: FavoriteTarget, val targetId: Id, val notifyLevel: NotifyLevel, val createdAt: OffsetDateTime, val seenAt: OffsetDateTime?)

// 응답의 favorited 표시용
data class FavoriteIds(val activities: Set<Id>, val programs: Set<Id>) {
    companion object {
        val NONE = FavoriteIds(emptySet(), emptySet())
    }
}
