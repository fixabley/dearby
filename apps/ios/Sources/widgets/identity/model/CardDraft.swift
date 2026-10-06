import Foundation

/// Composer state: the private profile plus which contacts and histories the new card shows.
struct CardDraft: Equatable {
    static let kinds = [("phone", "전화번호"), ("email", "이메일"), ("kakao", "카카오톡"),
                        ("instagram", "인스타그램"), ("github", "GitHub"), ("behance", "Behance")]
    var name = ""
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
        histories = saved.map { CardComposerHistory(id: $0.id, title: $0.title, detail: [$0.role, $0.startDate].filter { !$0.isEmpty }.joined(separator: " · "), isPublic: true) }
    }
    /// `PUT /v1/profile` body: filled contacts only, saved histories unchanged.
    var profile: AccountProfile {
        AccountProfile(name: name.trimmingCharacters(in: .whitespacesAndNewlines), job: job, introduction: introduction,
                       contacts: filled.map { AccountContact(id: $0.id, kind: $0.kind, label: $0.label, value: $0.value.trimmingCharacters(in: .whitespacesAndNewlines)) },
                       histories: saved)
    }
    /// The composer has no card-name field yet, so every card gets the same default name.
    var card: AccountClient.CardInput {
        AccountClient.CardInput(name: "내 명함", description: "", contactIds: filled.filter(\.isPublic).map(\.id),
                                historyIds: histories.filter(\.isPublic).map(\.id))
    }
    var canPublish: Bool { !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var filled: [CardComposerContact] { contacts.filter { !$0.value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }

    /// After signing in: keep what is on screen and the account's other saved data, so publishing never
    /// drops stored contacts or histories. Saved rows that were not on screen stay private.
    func merged(onto stored: AccountProfile) -> CardDraft {
        var result = CardDraft(profile: stored)
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
