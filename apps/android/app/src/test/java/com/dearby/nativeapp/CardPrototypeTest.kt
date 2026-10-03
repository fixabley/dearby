package com.dearby.nativeapp

import com.dearby.nativeapp.app.DemoViewModel
import com.dearby.nativeapp.app.demoPublicCard
import com.dearby.nativeapp.pages.wallet.walletMatches
import org.junit.Assert.*
import org.junit.Test

class CardPrototypeTest {
    @Test fun freshSessionResetsProfileLoginAndCardChanges() {
        val demo = DemoViewModel()
        demo.login(true)
        demo.profile(demo.state.value.profile.copy(name = "예시 이름"))
        demo.createCard(setOf("email"), setOf("conference"), "새 명함")
        val fresh = DemoViewModel().state.value
        assertFalse(fresh.loggedIn)
        assertEquals("김지민", fresh.profile.name)
        assertEquals(3, fresh.cards.size)
    }
    @Test fun cardCreationSnapshotsOnlyChosenContactAndHistory() {
        val demo = DemoViewModel()
        val card = demo.createCard(setOf("email"), setOf("conference"), "새 명함")
        assertEquals(listOf("email"), card.contacts.map { it.id })
        assertEquals(listOf("conference"), card.histories.map { it.id })
        demo.profile(demo.state.value.profile.copy(name = "바뀐 이름"))
        assertEquals("김지민", demo.state.value.cards.last().person)
        assertEquals(card.id, demo.state.value.selectedCardId)
    }
    @Test fun editingKeepsIdentityAndDoesNotCreateAnotherCard() {
        val demo = DemoViewModel()
        val before = demo.state.value.cards
        demo.editCard(before.first().id, setOf("email"), emptySet(), "수정한 명함")
        val after = demo.state.value.cards
        assertEquals(before.size, after.size)
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals("수정한 명함", after.first().title)
        assertEquals(listOf("email"), after.first().contacts.map { it.id })
        assertTrue(after.first().histories.isEmpty())
        assertEquals(before.drop(1), after.drop(1))
    }
    @Test fun cardSaveIsIdempotentAndSendMovesRecipientGroup() {
        val demo = DemoViewModel()
        demo.saveCard(demoPublicCard)
        demo.saveCard(demoPublicCard)
        assertEquals(1, demo.state.value.wallet.count { it.card.id == demoPublicCard.id })
        demo.send(demo.state.value.cards.first().id, demoPublicCard)
        assertTrue(demo.state.value.wallet.single { it.card.id == demoPublicCard.id }.reciprocal)
    }
    @Test fun walletSearchMatchesNameRoleAndActivity() {
        val entry = DemoViewModel().state.value.wallet.first()
        assertTrue(walletMatches(entry, "최유진"))
        assertTrue(walletMatches(entry, "디자이너"))
        assertTrue(walletMatches(entry, "해커톤"))
        assertFalse(walletMatches(entry, "없는 이름"))
    }
    @Test fun allRecipientsCanMoveToReciprocalWithoutExternalEffects() {
        val demo = DemoViewModel()
        val initial = demo.state.value
        initial.wallet.forEach { demo.send(initial.cards.first().id, it.card) }
        assertTrue(demo.state.value.wallet.all { it.reciprocal })
        assertEquals(initial.wallet.size, demo.state.value.wallet.size)
    }
}
