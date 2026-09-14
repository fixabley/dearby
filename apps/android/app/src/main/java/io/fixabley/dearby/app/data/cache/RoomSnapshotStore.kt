package io.fixabley.dearby.app.data.cache

import io.fixabley.dearby.app.data.NoticeSnapshot
import io.fixabley.dearby.entities.notice.api.NoticeRepository
import io.fixabley.dearby.entities.notice.api.NoticeSource
import io.fixabley.dearby.entities.notice.api.InMemoryNoticeSource
import io.fixabley.dearby.entities.notice.api.RoomNoticeStore
import io.fixabley.dearby.entities.notice.api.StoredNoticeSource
import io.fixabley.dearby.entities.organization.api.OrganizationRepository
import io.fixabley.dearby.entities.organization.api.OrganizationSource
import io.fixabley.dearby.entities.organization.api.InMemoryOrganizationSource
import io.fixabley.dearby.entities.organization.api.RoomOrganizationStore
import io.fixabley.dearby.entities.organization.api.StoredOrganizationSource
import java.util.concurrent.Callable

/** IO-only staging transaction. Its temporary L1s are discarded before memory-only UI publication. */
internal class RoomSnapshotStore(private val database: NoticeCacheDatabase) {
    fun prepare(snapshot: NoticeSnapshot,
        noticeSource: NoticeSource = InMemoryNoticeSource(snapshot.notices),
        organizationSource: OrganizationSource = InMemoryOrganizationSource(snapshot.organizations),
        checkActive: () -> Unit = {}, beforeCommit: () -> Unit = {},
    ): NoticeSnapshot = database.runInTransaction(Callable {
        checkActive()
        val next = SnapshotManifest.from(snapshot)
        val rows = database.manifest().all()
        require(rows.size <= 1 && (rows.firstOrNull()?.id ?: "current") == "current") { "Invalid manifest" }
        val previous = rows.firstOrNull()
        if (previous?.digest != next.digest) {
            database.notices().clear(); database.organizations().clear()
        } else {
            require(previous == next) { "Corrupt manifest metadata" }
        }
        val notices = NoticeRepository(StoredNoticeSource(RoomNoticeStore(database.notices()), noticeSource))
        val organizations = OrganizationRepository(StoredOrganizationSource(RoomOrganizationStore(database.organizations()), organizationSource))
        val loadedNotices = snapshot.notices.map { checkActive(); requireNotNull(notices.find(it.id)) { "Notice unavailable: ${it.id}" } }
        val loadedOrganizations = snapshot.organizations.map { checkActive(); requireNotNull(organizations.find(it.id)) { "Organization unavailable: ${it.id}" } }
        if (previous != next) database.manifest().upsert(next)
        beforeCommit(); checkActive()
        snapshot.copy(notices = loadedNotices, organizations = loadedOrganizations)
    })
}
