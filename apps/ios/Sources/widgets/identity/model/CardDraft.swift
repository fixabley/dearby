import Foundation

/// Composer state: the saved profile's data and which contacts and histories the card shows. The body comes from the
/// built-in starter template (contract "템플릿 — 2026-10-18 앱 안 꾸미기 삭제"); there is no in-app editing.
struct CardDraft: Equatable {
    var cardTitle = ""
    /// The built-in template the server fills at publish; fixed for the profile, never edited in the app.
    let template: String
    var publicContactIDs: Set<String>
    var publicHistoryIDs: Set<String>
    let profile: AccountProfile
    /// The state the composer opened with; differing from it switches on the 저장 / 원래대로 bar.
    let initial: Initial
    struct Initial: Equatable { let cardTitle: String; let contacts: Set<String>; let histories: Set<String> }

    /// A new card starts with every contact public and every approved history public;
    /// republishing an existing card starts from that card's name and selections.
    init(profile: AccountProfile, prefill: PublishedCard? = nil) {
        self.profile = profile
        cardTitle = prefill?.name ?? ""
        template = TemplateDocument.starter(for: profile)
        let approved = Set(profile.histories.filter(\.isApproved).map(\.id))
        publicContactIDs = prefill.map { Set($0.contacts.map(\.id)) } ?? Set(profile.contacts.map(\.id))
        publicHistoryIDs = prefill.map { Set($0.histories.map(\.id)).intersection(approved) } ?? approved
        initial = Initial(cardTitle: cardTitle, contacts: publicContactIDs, histories: publicHistoryIDs)
    }
    var changed: Bool { Initial(cardTitle: cardTitle, contacts: publicContactIDs, histories: publicHistoryIDs) != initial }
    mutating func revert() {
        cardTitle = initial.cardTitle; publicContactIDs = initial.contacts; publicHistoryIDs = initial.histories
    }
    /// Contacts the template names are always public (the server leaves unselected tokens empty).
    var tokenContactIDs: Set<String> { TemplateDocument.contactIDs(in: template) }
    var effectiveContactIDs: Set<String> { publicContactIDs.union(tokenContactIDs.intersection(profile.contacts.map(\.id))) }
    /// The body as the card will show it.
    var preview: String { TemplateDocument.fill(template, profile: profile, contactIDs: effectiveContactIDs) }
    var card: AccountClient.CardInput {
        let title = cardTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        return AccountClient.CardInput(name: title.isEmpty ? "내 명함" : title, description: "", template: template,
                                       contactIds: profile.contacts.map(\.id).filter(effectiveContactIDs.contains),
                                       historyIds: profile.histories.map(\.id).filter(publicHistoryIDs.contains))
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
