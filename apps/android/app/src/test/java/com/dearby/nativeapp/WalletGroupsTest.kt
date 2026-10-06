package com.dearby.nativeapp

import com.dearby.nativeapp.app.DemoViewModel
import com.dearby.nativeapp.app.demoWallet
import com.dearby.nativeapp.app.walletActivities
import com.dearby.nativeapp.pages.wallet.NO_ACTIVITY_GROUP
import com.dearby.nativeapp.pages.wallet.walletGroups
import com.dearby.nativeapp.shared.lib.matchesSearch
import org.junit.Assert.*
import org.junit.Test

class WalletGroupsTest {
    private val pending = demoWallet.filter { !it.reciprocal }
    private fun ids(query: String = "", collapsed: Set<String> = emptySet()) =
        walletGroups(pending, walletActivities, query, collapsed).associate { group -> group.id to group.entries.map { it.card.id } }

    @Test fun searchNeedsEveryTermInSomeFieldIgnoringCaseAndSpaces() {
        val fields = listOf("최유진", "Product Designer", "Dearby 개발자 컨퍼런스")
        assertTrue(matchesSearch("", fields))
        assertTrue(matchesSearch("   ", fields))
        assertTrue(matchesSearch("  유진  designer ", fields))
        assertTrue(matchesSearch("PRODUCT 컨퍼", fields))
        assertFalse(matchesSearch("유진 기획", fields))
    }
    @Test fun fixturesCoverTwoActivitiesAndNone() {
        assertTrue(demoWallet.any { it.activityIds.size == 2 })
        assertTrue(demoWallet.any { it.activityIds.isEmpty() })
        assertEquals(listOf("conference", "camp", "meetup"), walletActivities.map { it.first })
    }
    @Test fun cardsRepeatInEveryActivityAndNoActivityComesLast() {
        val groups = walletGroups(pending, walletActivities, "", emptySet())
        assertEquals(listOf("conference", "camp", NO_ACTIVITY_GROUP), groups.map { it.id })
        assertEquals(listOf("received-0", "received-1"), ids()["conference"])
        assertEquals(listOf("received-0"), ids()["camp"])
        assertEquals(listOf("received-2"), ids()[NO_ACTIVITY_GROUP])
        assertEquals("활동 없음", groups.last().title)
        val keys = groups.flatMap { group -> group.entries.map(group::key) }
        assertEquals(keys.size, keys.toSet().size)
    }
    @Test fun searchHidesEmptyGroupsAndShowsMatchesExpanded() {
        val groups = walletGroups(pending, walletActivities, "유진", setOf("camp"))
        assertEquals(listOf("conference", "camp"), groups.map { it.id })
        assertTrue(groups.all { it.expanded })
        assertFalse(walletGroups(pending, walletActivities, "", setOf("camp")).single { it.id == "camp" }.expanded)
        assertTrue(walletGroups(pending, walletActivities, "없는사람", emptySet()).isEmpty())
    }
    @Test fun collapsedGroupsLiveInSessionMemoryOnly() {
        val demo = DemoViewModel()
        demo.toggleGroup("conference")
        demo.query("유진")
        assertEquals(setOf("conference"), demo.state.value.collapsedGroupIds)
        demo.toggleGroup("conference")
        assertTrue(demo.state.value.collapsedGroupIds.isEmpty())
        assertTrue(DemoViewModel().state.value.collapsedGroupIds.isEmpty())
    }
}
