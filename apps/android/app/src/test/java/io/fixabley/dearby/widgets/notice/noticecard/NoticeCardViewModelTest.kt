package io.fixabley.dearby.widgets.notice.noticecard

import androidx.compose.runtime.snapshots.Snapshot
import io.fixabley.dearby.entities.notice.api.*
import io.fixabley.dearby.entities.organization.api.*
import io.fixabley.dearby.entities.organization.model.OrganizationModel
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import org.junit.Assert.*
import org.junit.Test

class NoticeCardViewModelTest {
    @Test fun sharedFavoritesUpdateExistingViewModelsAndComposeReaders() {
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {} })
        val notices = NoticeRepository(InMemoryNoticeSource(listOf(noticeFixture())))
        val organizations = OrganizationRepository(InMemoryOrganizationSource(listOf(OrganizationModel("org", "조직", null))))
        val first = NoticeCardViewModel("fixture", notices, organizations, favorites)
        val other = NoticeCardViewModel("fixture", notices, organizations, favorites)
        assertFalse(first.state!!.saved)
        other.save(); Snapshot.sendApplyNotifications()
        assertTrue(first.state!!.saved)
        favorites.remove("org"); Snapshot.sendApplyNotifications()
        assertFalse(first.state!!.saved)
        organizations.replaceSource(InMemoryOrganizationSource(listOf(OrganizationModel("org", "새 이름", null))))
        assertEquals("새 이름", first.state!!.organizationName)
    }
    @Test fun repeatedRenderingDoesNotRefetchMissingRecordsAndSnapshotCanRecover() {
        var reads = 0
        val notices = NoticeRepository(io.fixabley.dearby.entities.notice.api.NoticeSource { reads++; null })
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {} })
        val organizations = OrganizationRepository(InMemoryOrganizationSource(emptyList()))
        val vm = NoticeCardViewModel("fixture", notices, organizations, favorites)
        repeat(3) { assertNull(vm.state) }
        assertEquals(1, reads)
        notices.replaceSource(InMemoryNoticeSource(listOf(noticeFixture())))
        assertEquals("제목", vm.state!!.title)
        assertNull(vm.state!!.organizationName)
        assertEquals("저장할 조직을 확인 중이에요", vm.save())
        assertTrue(favorites.ids.isEmpty())
    }
}
