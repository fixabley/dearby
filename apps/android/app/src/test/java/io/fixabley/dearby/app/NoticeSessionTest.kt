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
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class NoticeSessionTest {
    @Test fun sameDetailAndObservedCardRefreshAfterAtomicSnapshotAndExternalFavoriteChanges() {
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = setOf("org"); override fun write(ids: Set<String>) {} })
        val initial = NoticeSnapshot("first", listOf(OrganizationModel("org", "이전", null)), listOf(noticeFixture()))
        val session = NoticeSession(NoticeSnapshotReader { initial }, favorites)
        kotlinx.coroutines.runBlocking { session.load() }
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
    @Test fun slowLoadCannotOverwriteExplicitReplacementAndCancellationPreservesState() = kotlinx.coroutines.runBlocking {
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {} })
        val old = NoticeSnapshot("old", emptyList(), emptyList())
        val newer = old.copy(snapshotDate = "new")
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val session = NoticeSession(NoticeSnapshotReader { started.countDown(); release.await(); old }, favorites)
        val job = async { session.load() }
        withContext(kotlinx.coroutines.Dispatchers.IO) { started.await() }
        session.replaceSnapshot(newer); release.countDown()
        try { job.await(); fail("superseded load") } catch (_: kotlinx.coroutines.CancellationException) {}
        assertEquals(newer, session.snapshot)
        val failed = NoticeSession(NoticeSnapshotReader { error("read failed") }, favorites)
        failed.replaceSnapshot(newer)
        try { failed.load(); fail("expected failure") } catch (_: IllegalStateException) {}
        assertEquals(newer, failed.snapshot)
    }
    @Test fun cancelledPreparationNeverPublishes() = kotlinx.coroutines.runBlocking {
        val favorites = FavoritesState(object : FavoriteStore { override fun read() = emptySet<String>(); override fun write(ids: Set<String>) {} })
        val previous = NoticeSnapshot("previous", emptyList(), emptyList())
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val session = NoticeSession(NoticeSnapshotReader { previous.copy(snapshotDate = "cancelled") }, favorites) { value, check ->
            started.countDown(); release.await(); check(); value
        }
        session.replaceSnapshot(previous)
        val job = async { session.load() }
        withContext(kotlinx.coroutines.Dispatchers.IO) { started.await() }
        job.cancel(); release.countDown(); job.join()
        assertEquals(previous, session.snapshot)
    }
}
