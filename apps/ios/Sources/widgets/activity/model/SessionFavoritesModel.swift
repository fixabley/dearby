import Foundation
import Observation

/// 세션 찜 (contract "트랙·세션 시간표" 2026-10-26): signed in only, apart from 즐겨찾기 (no activity heart, alert or 추천 signal). Every session
/// heart reads `ids`, built from `GET /v1/me/session-favorites` (timetables are read without a token). Signed out, or signed in before the
/// consent, a heart asks for the account (`signInRequested`; the app opens sign-in or the consent). A heart changes at once; a failed call
/// puts it back and says so in `notice`.
@MainActor @Observable final class SessionFavoritesModel {
    enum Phase: Equatable { case signedOut, loading, loaded, failed, unavailable }
    private(set) var phase = Phase.signedOut
    /// 내 시간표 in session start order.
    private(set) var items: [FavoriteSession] = []
    private(set) var ids: Set<Int64> = []
    var notice: String?
    var signInRequested = false
    /// The first session heart asks for the notification permission (찜한 세션 이동 알림), as the first program heart does.
    @ObservationIgnored var sessionFavorited: () -> Void = {}
    private var server: SessionFavoritesServer?

    func signedIn(_ server: SessionFavoritesServer) async {
        self.server = server
        await load()
    }
    func signedOut() {
        server = nil
        phase = .signedOut
        items = []
        ids = []
    }
    func load() async {
        guard let server else { return }
        if items.isEmpty && phase != .loaded { phase = .loading }
        do {
            let list = try await server.load()
            guard self.server != nil else { return }
            items = list ?? []
            ids = Set(items.map(\.session.id))
            phase = list == nil ? .unavailable : .loaded
        } catch {
            if phase == .loading { phase = .failed }
        }
    }
    /// The heart (`찜`/`찜 해제`).
    func toggle(_ sessionID: Int64) {
        guard let server else { signInRequested = true; return }
        let on = !ids.contains(sessionID)
        if on { ids.insert(sessionID) } else { ids.remove(sessionID); items.removeAll { $0.session.id == sessionID } }
        if on { sessionFavorited() }
        Task {
            do { try await server.set(sessionID, on) } catch {
                guard self.server != nil else { return }
                if on { ids.remove(sessionID) } else { ids.insert(sessionID) }
                notice = (error as? AccountError) == .unauthorized ? "로그인이 만료됐어요. 다시 로그인해 주세요."
                    : on ? "세션을 찜하지 못했어요. 잠시 후 다시 시도해 주세요." : "찜을 해제하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
            // The list (내 시간표, calendar days) follows the server; the hearts already show the change.
            await load()
        }
    }
}

/// The signed-in account's session-favorite calls, built by the app with the account's token refresh.
struct SessionFavoritesServer: Sendable {
    /// Nil when the server has no session favorites (404).
    let load: @Sendable () async throws -> [FavoriteSession]?
    /// `PUT` (true) or `DELETE` (false).
    let set: @Sendable (_ sessionID: Int64, _ on: Bool) async throws -> Void
}
