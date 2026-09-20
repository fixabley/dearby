package io.fixabley.dearby.pages.noticedetail.model

import io.fixabley.dearby.entities.notice.api.*
import io.fixabley.dearby.entities.notice.model.*
import io.fixabley.dearby.entities.organization.api.*
import io.fixabley.dearby.entities.organization.model.OrganizationModel
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import org.junit.Assert.*
import org.junit.Test

class NoticeDetailViewModelTest {
    @Test fun viewModelJoinsDisplayNamesPreservesSourceAndSeparatesOnlineFromFinalVenue() {
        val source = io.fixabley.dearby.entities.notice.model.NoticeSource("source", "https://example.org", "web", null, null, null)
        val evidence = NoticeEvidence("source", "일정", "schedule[0]", source.url)
        val model = noticeFixture().copy(contexts = listOf(NoticeContext("parent", "event_context")),
            sources = listOf(source), evidence = listOf(evidence),
            schedules = listOf(NoticePhase("preliminary", startsOn = "2026-10-14", mode = "online"), NoticePhase("final", startsOn = "2026-11-04", mode = "offline")),
            location = NoticeLocation("온라인 예선 / 결선", "mixed", "partial", listOf(NoticeVenue("final", "결선 장소", null, null))))
        var reads = 0
        val rows = listOf(OrganizationModel("org", "선택 조직", "parent"), OrganizationModel("parent", "상위", null), OrganizationModel("child", "하위", "org"))
        val organizations = OrganizationRepository(OrganizationSource { id -> reads++; rows.find { it.id == id } })
        val notices = NoticeRepository(InMemoryNoticeSource(listOf(model)))
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {} })
        val vm = NoticeDetailViewModel(model.id, notices, organizations, favorites)
        val state = vm.state!!
        assertEquals("선택 조직", state.organizationName)
        assertEquals(listOf("상위"), state.ancestorNames)
        assertEquals("행사 관련 기관", state.contexts.single().label)
        assertEquals("상위", state.contexts.single().name)
        // Evidence remains in the independent source/codec, not a second UI projection.
        val stored = NoticeStorageCodec.decode(NoticeStorageCodec.encode(notices.find(model.id)!!))
        assertEquals(model, stored)
        assertEquals(listOf(source), stored.sources); assertEquals(listOf(evidence), stored.evidence)
        assertEquals("parent", stored.contexts.single().organizationId)
        assertTrue(state.schedules.first().locations.isEmpty())
        assertEquals("결선 장소", state.schedules.last().locations.single().name)
        assertEquals(2, reads)
        assertEquals(state, NoticeDetailViewModel(model.id, notices, organizations, favorites).state)
        assertEquals(2, reads)
        favorites.save("org"); assertTrue(vm.state!!.saved)
        organizations.replaceSource(InMemoryOrganizationSource(listOf(OrganizationModel("org", "새 이름", "missing"))))
        assertEquals("새 이름", vm.state!!.organizationName)
        assertTrue(vm.state!!.ancestorNames.isEmpty())
        assertNull(vm.state!!.contexts.single().name)
        notices.replaceSource(InMemoryNoticeSource(emptyList())); assertNull(vm.state)
    }
}
