import Foundation

/// Composer state: the saved profile's data and which contacts, histories, photo and 소개 링크 the card shows. There is no body
/// (contract "소개 링크·소개 글 폐지" 2026-10-26) and no in-app editing.
struct CardDraft: Equatable {
    var cardTitle = ""
    var publicContactIDs: Set<String>
    var publicHistoryIDs: Set<String>
    /// 사진 넣기: on for a new card; editing, on when that card has a photo. Only offered when the profile has one.
    var includePhoto: Bool
    /// 소개 링크 넣기: like 사진 넣기 — on for a new card; editing, on when that card has a link (same as Android). Only offered when the profile has a link.
    var includeIntroLink: Bool
    let profile: AccountProfile
    /// The state the composer opened with; differing from it switches on the 저장 / 원래대로 bar.
    let initial: Initial
    struct Initial: Equatable { let cardTitle: String; let contacts: Set<String>; let histories: Set<String>; let includePhoto: Bool; let includeIntroLink: Bool }

    /// A new card starts with every contact public and every approved history public;
    /// editing an existing card (이 명함 고치기) starts from that card's name and selections.
    init(profile: AccountProfile, prefill: PublishedCard? = nil) {
        self.profile = profile
        cardTitle = prefill?.name ?? ""
        let approved = Set(profile.histories.filter(\.isApproved).map(\.id))
        publicContactIDs = prefill.map { Set($0.contacts.map(\.id)) } ?? Set(profile.contacts.map(\.id))
        publicHistoryIDs = prefill.map { Set($0.histories.map(\.id)).intersection(approved) } ?? approved
        includePhoto = prefill.map { $0.photoUrl != nil } ?? true
        includeIntroLink = prefill.map { $0.introLink != nil } ?? true
        initial = Initial(cardTitle: cardTitle, contacts: publicContactIDs, histories: publicHistoryIDs, includePhoto: includePhoto, includeIntroLink: includeIntroLink)
    }
    var changed: Bool {
        Initial(cardTitle: cardTitle, contacts: publicContactIDs, histories: publicHistoryIDs, includePhoto: includePhoto, includeIntroLink: includeIntroLink) != initial
    }
    mutating func revert() {
        cardTitle = initial.cardTitle; publicContactIDs = initial.contacts; publicHistoryIDs = initial.histories; includePhoto = initial.includePhoto
        includeIntroLink = initial.includeIntroLink
    }
    var card: AccountClient.CardInput {
        let title = cardTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        return AccountClient.CardInput(name: title.isEmpty ? "내 명함" : title, description: "",
                                       contactIds: profile.contacts.map(\.id).filter(publicContactIDs.contains),
                                       historyIds: profile.histories.map(\.id).filter(publicHistoryIDs.contains),
                                       includePhoto: profile.photoUrl == nil ? nil : includePhoto,
                                       includeIntroLink: profile.introLink == nil ? nil : includeIntroLink)
    }
    /// Why the profile cannot be published yet (name, phone and email are required data), or nil.
    var problem: String? {
        if profile.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "프로필에서 이름을 먼저 입력해 주세요." }
        if !profile.contacts.contains(where: { $0.kind == "phone" }) || !profile.contacts.contains(where: { $0.kind == "email" }) {
            return "프로필에서 전화번호와 이메일을 먼저 입력해 주세요."
        }
        return nil
    }
}
