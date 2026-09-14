package io.fixabley.dearby.app

import androidx.test.platform.app.InstrumentationRegistry
import io.fixabley.dearby.app.data.AssetNoticeSnapshotReader
import io.fixabley.dearby.app.data.cache.NoticeCacheDatabase
import io.fixabley.dearby.app.data.cache.RoomSnapshotStore
import io.fixabley.dearby.entities.notice.api.*
import io.fixabley.dearby.entities.organization.api.*
import io.fixabley.dearby.entities.organization.model.OrganizationModel
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RoomSnapshotStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun snapshot() = AssetNoticeSnapshotReader(context.assets).load()
    private fun database(block: (String, NoticeCacheDatabase) -> Unit) {
        val name = "room-test-${UUID.randomUUID()}.db"
        val db = NoticeCacheDatabase.open(context, name)
        try { block(name, db) } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun realDiskReopenRetainsAllPayloadFieldsAndMakesZeroExternalFetches() = database { name, db ->
        val original = snapshot()
        var noticeFetches = 0; var organizationFetches = 0
        val notices = InMemoryNoticeSource(original.notices)
        val organizations = InMemoryOrganizationSource(original.organizations)
        val first = RoomSnapshotStore(db).prepare(original,
            NoticeSource { noticeFetches++; notices.find(it) }, OrganizationSource { organizationFetches++; organizations.find(it) })
        assertEquals(original, first)
        assertEquals(original.notices.size, noticeFetches); assertEquals(original.organizations.size, organizationFetches)
        db.close()
        val reopened = NoticeCacheDatabase.open(context, name)
        try {
            val second = RoomSnapshotStore(reopened).prepare(original,
                NoticeSource { noticeFetches++; error("L2 must satisfy") }, OrganizationSource { organizationFetches++; error("L2 must satisfy") })
            assertEquals(original, second)
            assertEquals(original.notices.size, noticeFetches); assertEquals(original.organizations.size, organizationFetches)
            var diskReads = 0
            val disk = RoomNoticeStore(reopened.notices())
            val repo = NoticeRepository(StoredNoticeSource(object : NoticeDiskStore {
                override fun find(id: String) = disk.find(id).also { diskReads++ }
                override fun upsert(value: io.fixabley.dearby.entities.notice.model.NoticeModel) = disk.upsert(value)
            }, NoticeSource { error("unexpected") }))
            val id = original.notices.first().id
            assertEquals(repo.find(id), repo.find(id)); assertEquals(1, diskReads)
        } finally { reopened.close() }
    }

    @Test fun changedSnapshotRollbackThenReplacementDeletesAndReparentsWithoutTouchingFavorites() = database { _, db ->
        val original = snapshot()
        val prefs = context.getSharedPreferences("dearby.favorites.v1", 0).all.toMap()
        val store = RoomSnapshotStore(db); store.prepare(original)
        val manifest = db.manifest().all()
        val first = original.organizations.first()
        val changed = original.copy(contentHash = "changed", notices = original.notices.dropLast(1).map { it.copy(title = "변경 ${it.title}") },
            organizations = listOf(OrganizationModel(first.id, "새 이름", "new-parent"), OrganizationModel("new-parent", "새 부모", null)))
        assertThrows(IllegalStateException::class.java) { store.prepare(changed, beforeCommit = { error("commit failure") }) }
        assertEquals(manifest, db.manifest().all())
        assertEquals(original.notices.last(), RoomNoticeStore(db.notices()).find(original.notices.last().id))
        assertEquals(original, store.prepare(original, NoticeSource { error("old preserved") }, OrganizationSource { error("old preserved") }))
        assertEquals(changed, store.prepare(changed))
        assertNull(db.notices().find(original.notices.last().id))
        val org = OrganizationRepository(StoredOrganizationSource(RoomOrganizationStore(db.organizations()), OrganizationSource { error("stored") }))
        assertEquals(listOf("새 부모", "새 이름"), org.path(first.id).map { it.name })
        assertEquals(prefs, context.getSharedPreferences("dearby.favorites.v1", 0).all)
        assertEquals(changed.copy(notices = emptyList(), organizations = emptyList()), store.prepare(changed.copy(notices = emptyList(), organizations = emptyList())))
        assertNull(db.organizations().find(first.id))
    }

    @Test fun sqliteWriteFailureExternalFailureAndCancellationRollbackBothSlicesAndManifest() = database { _, db ->
        val original = snapshot(); val store = RoomSnapshotStore(db); store.prepare(original)
        val changed = original.copy(contentHash = "changed")
        val manifest = db.manifest().all()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_notice BEFORE INSERT ON notices BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
        assertThrows(Exception::class.java) { store.prepare(changed) }
        assertEquals(manifest, db.manifest().all())
        assertEquals(original.notices.first(), RoomNoticeStore(db.notices()).find(original.notices.first().id))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_notice")
        assertThrows(IllegalStateException::class.java) { store.prepare(changed, noticeSource = NoticeSource { error("external failure") }) }
        assertThrows(CancellationException::class.java) { store.prepare(changed, beforeCommit = { throw CancellationException("cancel") }) }
        assertEquals(manifest, db.manifest().all())
        assertEquals(original, store.prepare(original, NoticeSource { error("preserved") }, OrganizationSource { error("preserved") }))
    }

    @Test fun corruptPayloadAndMismatchedIdDoNotFetchOrOverwrite() = database { _, db ->
        val original = snapshot(); RoomSnapshotStore(db).prepare(original)
        val id = original.notices.first().id
        val wrong = original.notices.first().copy(id = "wrong")
        db.notices().upsert(NoticeRecord(id, NoticeStorageCodec.VERSION, NoticeStorageCodec.encode(wrong)))
        var fetches = 0
        val source = StoredNoticeSource(RoomNoticeStore(db.notices()), NoticeSource { fetches++; null })
        assertThrows(IllegalArgumentException::class.java) { NoticeRepository(source).find(id) }
        assertEquals(0, fetches)
        assertEquals("wrong", NoticeStorageCodec.decode(db.notices().find(id)!!.payload).id)
        db.notices().upsert(NoticeRecord(id, NoticeStorageCodec.VERSION, byteArrayOf(1)))
        assertThrows(Exception::class.java) { source.find(id) }; assertEquals(0, fetches)
        db.close()
        assertThrows(IllegalStateException::class.java) { source.find(id) }
    }

    @Test fun corruptDatabaseOpenPreservesOriginalFileInsteadOfDestructiveRecovery() {
        val name = "room-corrupt-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val bytes = ByteArray(4096) { 42 }; file.writeBytes(bytes)
        val db = NoticeCacheDatabase.open(context, name)
        try {
            assertThrows(Exception::class.java) { RoomSnapshotStore(db).prepare(snapshot()) }
            assertArrayEquals(bytes, file.readBytes())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
