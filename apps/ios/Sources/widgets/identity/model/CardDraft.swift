import Foundation

/// Composer state: the private profile plus which contacts and histories the new card shows.
struct CardDraft: Equatable {
    static let kinds = [("phone", "전화번호"), ("email", "이메일"), ("kakao", "카카오톡"),
                        ("instagram", "인스타그램"), ("github", "GitHub"), ("behance", "Behance")]
    var name = ""
    /// Optional card name (#146); empty means "내 명함".
    var cardTitle = ""
    var job = ""
    var introduction = ""
    var contacts: [CardComposerContact]
    var histories: [CardComposerHistory] = []
    private var saved: [AccountHistory] = []

    /// Saved contacts first, then one empty row for each kind the profile does not have yet.
    init(profile: AccountProfile? = nil) {
        name = profile?.name ?? ""
        job = profile?.job ?? ""
        introduction = profile?.introduction ?? ""
        let existing = (profile?.contacts ?? []).map { CardComposerContact(id: $0.id, kind: $0.kind, label: $0.label, value: $0.value, isPublic: true) }
        contacts = existing + Self.kinds.filter { kind in !existing.contains { $0.kind == kind.0 } }
            .map { CardComposerContact(id: UUID().uuidString.lowercased(), kind: $0.0, label: $0.1, value: "", isPublic: true) }
        saved = profile?.histories ?? []
        histories = saved.map { CardComposerHistory(id: $0.id, title: $0.title,
            detail: [$0.role, ContactRules.period(start: $0.startDate, end: $0.endDate)].filter { !$0.isEmpty }.joined(separator: " · "), isPublic: true) }
    }
    /// `PUT /v1/profile` body: filled contacts only, saved histories unchanged.
    var profile: AccountProfile {
        AccountProfile(name: name.trimmingCharacters(in: .whitespacesAndNewlines), job: job, introduction: introduction,
                       contacts: filled.map { AccountContact(id: $0.id, kind: $0.kind, label: $0.label, value: $0.value.trimmingCharacters(in: .whitespacesAndNewlines)) },
                       histories: saved)
    }
    /// The card's own name: what was typed, or "내 명함".
    var card: AccountClient.CardInput {
        AccountClient.CardInput(name: cardTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "내 명함" : cardTitle.trimmingCharacters(in: .whitespacesAndNewlines), description: "", contactIds: filled.filter(\.isPublic).map(\.id),
                                historyIds: histories.filter(\.isPublic).map(\.id))
    }
    var canPublish: Bool { problem == nil }
    /// Why the profile cannot be saved yet (contract #149: name, a phone and an email), or nil.
    var problem: String? {
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "이름을 입력해 주세요." }
        let phone = contacts.first { $0.kind == "phone" }?.value ?? "", email = contacts.first { $0.kind == "email" }?.value ?? ""
        if phone.trimmingCharacters(in: .whitespaces).isEmpty { return "전화번호를 입력해 주세요." }
        if !ContactRules.validPhone(phone) { return "전화번호는 숫자 8~15자리로 입력해 주세요." }
        if email.trimmingCharacters(in: .whitespaces).isEmpty { return "이메일을 입력해 주세요." }
        if !ContactRules.validEmail(email) { return "이메일 주소를 확인해 주세요." }
        return nil
    }
    private var filled: [CardComposerContact] { contacts.filter { !$0.value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }

    /// After signing in: keep what is on screen and the account's other saved data, so publishing never
    /// drops stored contacts or histories. Saved rows that were not on screen stay private.
    func merged(onto stored: AccountProfile) -> CardDraft {
        var result = CardDraft(profile: stored)
        result.cardTitle = cardTitle
        if canPublish { result.name = name }
        if !job.isEmpty { result.job = job }
        if !introduction.isEmpty { result.introduction = introduction }
        for index in result.contacts.indices where !result.contacts[index].value.isEmpty {
            result.contacts[index].isPublic = contacts.first { $0.id == result.contacts[index].id }?.isPublic ?? false
        }
        for typed in filled {
            if let index = result.contacts.firstIndex(where: { $0.id == typed.id })
                ?? result.contacts.firstIndex(where: { $0.kind == typed.kind && $0.value.isEmpty }) {
                result.contacts[index].value = typed.value
                result.contacts[index].isPublic = typed.isPublic
            } else {
                result.contacts.append(typed)
            }
        }
        for index in result.histories.indices {
            result.histories[index].isPublic = histories.first { $0.id == result.histories[index].id }?.isPublic ?? false
        }
        return result
    }
}
