package com.dearby.nativeapp.pages.wallet

import com.dearby.nativeapp.shared.lib.matchesSearch

const val NO_ACTIVITY_GROUP = "none"

data class WalletGroupState(val id: String, val title: String, val entries: List<WalletEntryState>, val expanded: Boolean) {
    // A card can sit in several groups, so list keys combine both IDs.
    fun key(entry: WalletEntryState) = "$id/${entry.card.id}"
}

/**
 * Groups the wallet by shared activity. [activities] are (id, title) in the order they were first saved.
 * Empty groups are hidden; while searching every remaining group is shown expanded
 * without touching [collapsed].
 */
fun walletGroups(entries: List<WalletEntryState>, activities: List<Pair<String, String>>, query: String, collapsed: Set<String>): List<WalletGroupState> {
    val titles = activities.toMap()
    val matches = entries.filter { entry ->
        val card = entry.card
        matchesSearch(query, listOf(card.person, card.job, card.title, card.description, card.introduction) +
            card.histories.map { it.title } + entry.activityIds.mapNotNull(titles::get))
    }
    val searching = query.isNotBlank()
    return (activities + (NO_ACTIVITY_GROUP to "활동 없음")).mapNotNull { (id, title) ->
        val members = matches.filter { entry ->
            if (id == NO_ACTIVITY_GROUP) entry.activityIds.none(titles::containsKey) else id in entry.activityIds
        }
        if (members.isEmpty()) null else WalletGroupState(id, title, members, searching || id !in collapsed)
    }
}
