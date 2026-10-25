package com.dearby.nativeapp.entities.activity.model

/**
 * 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25): `GET /v1/me/favorites`. [FavoriteProgram.newRecruitment] is true once an
 * activity posted after the heart opened, until the program is opened from 즐겨찾기 (`…/seen`). Activities come in the server's order
 * (nearest deadline first, past ones last). [NotifyLevel] is the per-favorite 알림 수준; an older server without it reads as [NotifyLevel.APPLICATION].
 */
data class Favorites(val programs: List<FavoriteProgram>, val activities: List<FavoriteActivity>)
data class FavoriteProgram(val program: ProgramSummary, val favoritedAt: String?, val newRecruitment: Boolean, val notifyLevel: NotifyLevel)
data class FavoriteActivity(val activity: ActivityModel, val program: ProgramSummary, val favoritedAt: String?, val notifyLevel: NotifyLevel)

/** `전체` (every schedule + new activities) · `신청일만` (recruitment start and end, the default) · `없음`. */
enum class NotifyLevel { ALL, APPLICATION, NONE;
    companion object { fun of(code: String?) = entries.firstOrNull { it.name == code } ?: APPLICATION }
}

/**
 * 알림 목록 (contract "알림 목록(발견 우상단 종)", 2026-10-25): `GET /v1/me/notifications` groups by program, most recent first. [InboxItem.kind] is
 * the server's code (`NEW`·`APPLICATION_START`·`DEADLINE_EVE`·`DEADLINE`·`ANNOUNCEMENT`·`INTERVIEW`·`EVENT`·`ACTIVITY_START`).
 */
data class InboxPage(val groups: List<InboxGroup>, val nextCursor: String?)
data class InboxGroup(val program: ProgramSummary, val unread: Int, val items: List<InboxItem>)
data class InboxItem(val id: String, val kind: String, val activityId: String, val activityName: String, val createdAt: java.time.Instant?, val read: Boolean)
