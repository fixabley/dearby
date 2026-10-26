package com.dearby.nativeapp.pages.inbox

import java.time.Instant

enum class InboxPhase { LOADING, FAILED, LOADED }
/** One notification row: [kind] is the chip text (`새 모집`·`마감 D-1`…), [received] the time text (`오늘 08:00`·`어제`·`10월 21일`). */
data class InboxRow(val id: String, val activityId: String, val title: String, val kind: String, val received: String, val unread: Boolean)
/** One program card: the discovery card head ([logoUrl], [title], [organization], 종류 [category]·[categoryCode]) with the program's [unread] count in the count slot. */
data class InboxCard(
    val programId: String, val title: String, val organization: String, val logoUrl: String?, val unread: Int, val rows: List<InboxRow>, val latest: Instant?,
    val category: String? = null, val categoryCode: String? = null,
)
/** [recent] are the cards whose latest notification is in the last 7 days (`이번 주`), [older] the rest (`지난 알림`). */
data class InboxViewState(
    val phase: InboxPhase = InboxPhase.LOADING, val recent: List<InboxCard> = emptyList(), val older: List<InboxCard> = emptyList(),
    val unread: Int = 0, val nextCursor: String? = null, val loadingMore: Boolean = false,
)
