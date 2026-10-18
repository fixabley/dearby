import Foundation

/// Composer state: the saved profile's data, the decoration template (plus the optional web version) and which
/// contacts and histories the card shows.
struct CardDraft: Equatable {
    enum Version: Hashable { case mobile, web }
    var cardTitle = ""
    var template: String
    /// The web content version (contract "웹 버전"); nil means the card has none and the web shows the mobile body.
    var webTemplate: String?
    var publicContactIDs: Set<String>
    var publicHistoryIDs: Set<String>
    let profile: AccountProfile
    /// The state the composer opened with; differing from it switches on the 저장 / 원래대로 bar.
    let initial: Initial
    struct Initial: Equatable { let cardTitle: String; let template: String; let webTemplate: String?; let contacts: Set<String>; let histories: Set<String> }

    /// A new card starts from the starter template with every contact public and every approved history public;
    /// republishing an existing card starts from that card's template and selections.
    init(profile: AccountProfile, prefill: PublishedCard? = nil) {
        self.profile = profile
        cardTitle = prefill?.name ?? ""
        template = prefill?.template ?? TemplateDocument.starter(for: profile)
        webTemplate = prefill?.webTemplate
        let approved = Set(profile.histories.filter(\.isApproved).map(\.id))
        publicContactIDs = prefill.map { Set($0.contacts.map(\.id)) } ?? Set(profile.contacts.map(\.id))
        publicHistoryIDs = prefill.map { Set($0.histories.map(\.id)).intersection(approved) } ?? approved
        initial = Initial(cardTitle: cardTitle, template: template, webTemplate: webTemplate, contacts: publicContactIDs, histories: publicHistoryIDs)
    }
    var changed: Bool { Initial(cardTitle: cardTitle, template: template, webTemplate: webTemplate, contacts: publicContactIDs, histories: publicHistoryIDs) != initial }
    mutating func revert() {
        cardTitle = initial.cardTitle; template = initial.template; webTemplate = initial.webTemplate
        publicContactIDs = initial.contacts; publicHistoryIDs = initial.histories
    }
    /// Whether a separate web version is being written. Turning it on starts from a copy of the mobile body; off drops it.
    var hasWebVersion: Bool {
        get { webTemplate != nil }
        set { webTemplate = newValue ? (webTemplate ?? template) : nil }
    }
    /// The template of one version (web reads as empty when off; writing to web while off turns it on).
    func template(for version: Version) -> String { version == .mobile ? template : (webTemplate ?? "") }
    mutating func setTemplate(_ value: String, for version: Version) {
        if version == .mobile { template = value } else { webTemplate = value }
    }
    /// Contacts either template names are always public (the server leaves unselected tokens empty).
    var tokenContactIDs: Set<String> { TemplateDocument.contactIDs(in: template).union(TemplateDocument.contactIDs(in: webTemplate ?? "")) }
    var effectiveContactIDs: Set<String> { publicContactIDs.union(tokenContactIDs.intersection(profile.contacts.map(\.id))) }
    /// The body of one version as the card will show it.
    func preview(for version: Version) -> String { TemplateDocument.fill(template(for: version), profile: profile, contactIDs: effectiveContactIDs) }
    var card: AccountClient.CardInput {
        let title = cardTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let web = webTemplate?.trimmingCharacters(in: .whitespacesAndNewlines)
        return AccountClient.CardInput(name: title.isEmpty ? "내 명함" : title, description: "", template: template,
                                       webTemplate: web?.isEmpty == false ? webTemplate : nil,
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
