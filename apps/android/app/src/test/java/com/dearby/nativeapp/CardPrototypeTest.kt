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
        val fresh = DemoViewModel().state.value
        assertFalse(fresh.loggedIn)
        assertEquals("김지민", fresh.profile.name)
        assertEquals(3, fresh.cards.size)
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
