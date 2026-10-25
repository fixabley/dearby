package io.wid.dearby.application.favorite

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.favorite.Favorite
import io.wid.dearby.domain.favorite.FavoriteTarget
import io.wid.dearby.domain.favorite.NotifyLevel
import java.time.OffsetDateTime

// 저장소 포트. 구현은 adaptor/persistence. users cascade라 계정 삭제와 함께 지워진다
interface FavoriteRepository {

    // 없으면 넣고(level이 없으면 기본값), 있으면 level이 있을 때만 수준을 바꾼다
    fun save(userId: UserId, target: FavoriteTarget, targetId: Id, level: NotifyLevel?, at: OffsetDateTime)
    fun delete(userId: UserId, target: FavoriteTarget, targetId: Id)
    fun markSeen(userId: UserId, programId: Id, at: OffsetDateTime)
    fun findAll(userId: UserId): List<Favorite>
    fun userIds(): List<UserId> // 즐겨찾기가 하나라도 있는 사용자(알림 목록을 만들 대상)

    // 새 모집: 즐겨찾기한 프로그램에 즐겨찾기(또는 마지막으로 연 시각) 뒤 게시된 활동 중 지금 열린 것. 사용자 → (프로그램 → 활동 id들)
    fun newRecruitments(userIds: Collection<UserId>, now: OffsetDateTime): Map<UserId, Map<Id, List<Id>>>
}
