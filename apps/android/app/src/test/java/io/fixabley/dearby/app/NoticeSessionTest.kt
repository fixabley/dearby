package io.fixabley.dearby.app

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import io.fixabley.dearby.app.data.*
import io.fixabley.dearby.entities.notice.api.noticeFixture
import io.fixabley.dearby.entities.organization.model.OrganizationModel
import io.fixabley.dearby.features.favoriteorganization.api.FavoriteStore
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import org.junit.Assert.*
import org.junit.Test

class NoticeSessionTest {
    @Test fun sameDetailAndObservedCardRefreshAfterAtomicSnapshotAndExternalFavoriteChanges() {
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = setOf("org"); override fun write(ids: Set<String>) {} })
        val initial = NoticeSnapshot("first", listOf(OrganizationModel("org", "이전", null)), listOf(noticeFixture()))
        val session = NoticeSession(NoticeSnapshotReader { initial }, favorites)
        session.load()
        val detail = session.detail("fixture")
        assertTrue(detail.state!!.saved)
        val observer = SnapshotStateObserver { it() }
        var invalidations = 0
        observer.start()
        try {
            observer.observeReads("card", { _: String -> invalidations++ }) { assertTrue(session.cardStates().single().saved) }
            favorites.remove("org"); Snapshot.sendApplyNotifications()
            assertTrue(invalidations > 0)
            assertFalse(detail.state!!.saved)
            session.replaceSnapshot(NoticeSnapshot("second", listOf(OrganizationModel("org", "새 이름", "parent"), OrganizationModel("parent", "새 부모", null)), listOf(noticeFixture().copy(title = "새 제목"))))
            Snapshot.sendApplyNotifications()
            assertSame(detail, session.detail("fixture"))
            assertEquals("새 이름", detail.state!!.organizationName)
            assertEquals(listOf("새 부모"), detail.state!!.ancestorNames)
            assertEquals("새 제목", session.cardStates().single().title)
            session.replaceSnapshot(NoticeSnapshot("empty", emptyList(), emptyList()))
            assertTrue(session.cardStates().isEmpty()); assertNull(detail.state)
        } finally { observer.stop(); observer.clear() }
    }
}
