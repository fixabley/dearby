package io.fixabley.dearby.widgets.favoriteorganizationcard.model

import io.fixabley.dearby.entities.notice.api.*
import io.fixabley.dearby.entities.organization.api.*
import io.fixabley.dearby.entities.organization.model.OrganizationModel
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.widgets.noticecard.model.NoticeCardViewModel
import org.junit.Assert.*
import org.junit.Test

class FavoriteOrganizationViewModelTest {
    @Test fun removalFromFavoriteCardUpdatesExistingDiscoveryCardAndKeepsParentRendering() {
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = setOf("org"); override fun write(ids: Set<String>) {} })
        val notices = NoticeRepository(InMemoryNoticeSource(listOf(noticeFixture())))
        val organizations = OrganizationRepository(InMemoryOrganizationSource(listOf(OrganizationModel("org", "조직", "parent"), OrganizationModel("parent", "부모", null))))
        val discovery = NoticeCardViewModel("fixture", notices, organizations, favorites)
        val favorite = FavoriteOrganizationCardViewModel("org", listOf("fixture"), notices, organizations, favorites)
        assertTrue(discovery.state!!.saved)
        assertEquals(listOf("부모"), favorite.state!!.ancestorNames)
        assertEquals("fixture", favorite.state!!.notices.single().id)
        favorite.remove()
        assertFalse(discovery.state!!.saved)
        assertNull(favorite.state)
        discovery.save()
        assertNotNull(favorite.state)
        notices.replaceSource(InMemoryNoticeSource(emptyList()))
        assertTrue(favorite.state!!.notices.isEmpty())
    }
}
