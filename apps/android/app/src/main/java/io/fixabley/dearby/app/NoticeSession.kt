package io.fixabley.dearby.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import io.fixabley.dearby.app.data.NoticeSnapshot
import io.fixabley.dearby.app.data.NoticeSnapshotReader
import io.fixabley.dearby.entities.notice.api.NoticeRepository
import io.fixabley.dearby.entities.notice.api.InMemoryNoticeSource
import io.fixabley.dearby.entities.organization.api.OrganizationRepository
import io.fixabley.dearby.entities.organization.api.InMemoryOrganizationSource
import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCardViewModel
import io.fixabley.dearby.widgets.organization.favoriteorganizationcard.FavoriteOrganizationCardViewModel
import io.fixabley.dearby.pages.noticedetail.model.NoticeDetailViewModel

/** App/UI-thread owner; readers are called only by load, never from rendering Views. */
internal class NoticeSession(private val reader: NoticeSnapshotReader, private val favorites: FavoritesState) {
    val notices = NoticeRepository(InMemoryNoticeSource(emptyList()))
    val organizations = OrganizationRepository(InMemoryOrganizationSource(emptyList()))
    var snapshot: NoticeSnapshot? by mutableStateOf(null)
        private set
    private val cards = mutableMapOf<String, NoticeCardViewModel>()
    private val details = mutableMapOf<String, NoticeDetailViewModel>()
    private var favoriteCards = emptyList<FavoriteOrganizationCardViewModel>()
    fun load(): NoticeSnapshot = reader.load().also { if (snapshot !== it) replaceSnapshot(it) }
    fun replaceSnapshot(value: NoticeSnapshot) {
        Snapshot.withMutableSnapshot {
            notices.replaceSource(InMemoryNoticeSource(value.notices))
            organizations.replaceSource(InMemoryOrganizationSource(value.organizations))
            value.notices.forEach { notice -> cards.getOrPut(notice.id) { NoticeCardViewModel(notice.id, notices, organizations, favorites) } }
            cards.keys.retainAll(value.notices.map { it.id }.toSet())
            favoriteCards = value.organizations.map { FavoriteOrganizationCardViewModel(it.id, value.notices.map { notice -> notice.id }, notices, organizations, favorites) }
            snapshot = value
        }
    }
    fun cardStates() = snapshot?.notices.orEmpty().mapNotNull { cards[it.id]?.state }
    fun save(id: String) = cards[id]?.save() ?: "저장할 조직을 확인 중이에요"
    fun favoriteStates() = favoriteCards.mapNotNull { it.state }
    fun remove(id: String) = favorites.remove(id)
    fun detail(id: String) = details.getOrPut(id) { NoticeDetailViewModel(id, notices, organizations, favorites) }
}
