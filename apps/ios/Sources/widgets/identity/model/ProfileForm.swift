import Foundation

/// 내 프로필 data (contract "프로필과 버전 이력"): name, job, introduction, required phone and email, other contacts
/// by kind, and histories entered through the step funnel. `version` goes back as `If-Match`.
struct ProfileForm: Equatable {
    var name: String
    var job: String
    var introduction: String
    var phone: String
    var email: String
    var extras: [ProfileExtraContact]
    var histories: [AccountHistory]
    let version: Int
    private var phoneID: String
    private var emailID: String

    init(profile: AccountProfile) {
        name = profile.name
        job = profile.job
        introduction = profile.introduction
        version = profile.version
        let phoneRow = profile.contacts.first { $0.kind == "phone" }, emailRow = profile.contacts.first { $0.kind == "email" }
        phone = phoneRow?.value ?? ""
        email = emailRow?.value ?? ""
        phoneID = phoneRow?.id ?? UUID().uuidString.lowercased()
        emailID = emailRow?.id ?? UUID().uuidString.lowercased()
        extras = profile.contacts.filter { $0.id != phoneRow?.id && $0.id != emailRow?.id }
            .map { ProfileExtraContact(id: $0.id, kind: $0.kind, value: $0.value) }
        histories = profile.histories
    }
    mutating func addContact(_ kind: String) { extras.append(ProfileExtraContact(id: UUID().uuidString.lowercased(), kind: kind, value: "")) }
    /// Puts a finished funnel draft in place (same id) or appends it.
    mutating func put(_ draft: HistoryDraft) {
        let history = draft.history
        if let index = histories.firstIndex(where: { $0.id == history.id }) { histories[index] = history } else { histories.append(history) }
    }
    mutating func removeHistory(_ id: String) { histories.removeAll { $0.id == id } }

    var nameError: String? { name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "이름을 입력해 주세요." : nil }
    var phoneError: String? {
        if phone.trimmingCharacters(in: .whitespaces).isEmpty { return "전화번호를 입력해 주세요." }
        return ContactRules.validPhone(phone) ? nil : "전화번호는 숫자 8~15자리로 입력해 주세요."
    }
    var emailError: String? {
        if email.trimmingCharacters(in: .whitespaces).isEmpty { return "이메일을 입력해 주세요." }
        return ContactRules.validEmail(email) ? nil : "이메일 주소를 확인해 주세요."
    }
    var isValid: Bool { nameError == nil && phoneError == nil && emailError == nil }

    /// `PUT /v1/profile` body. Empty added contacts are left out.
    var profile: AccountProfile {
        let labels = Dictionary(uniqueKeysWithValues: ProfileContactFields.kinds.map { ($0.kind, $0.label) })
        let trimmed = { (text: String) in text.trimmingCharacters(in: .whitespacesAndNewlines) }
        return AccountProfile(
            name: trimmed(name), job: trimmed(job), introduction: trimmed(introduction),
            contacts: [AccountContact(id: phoneID, kind: "phone", label: "전화번호", value: trimmed(phone)),
                       AccountContact(id: emailID, kind: "email", label: "이메일", value: trimmed(email))]
                + extras.filter { !trimmed($0.value).isEmpty }
                    .map { AccountContact(id: $0.id, kind: $0.kind, label: labels[$0.kind] ?? $0.kind, value: trimmed($0.value)) },
            histories: histories, version: version)
    }
    /// What the server would receive, for change detection against the loaded profile.
    var changed: Bool { ProfileForm(profile: profile).profile != ProfileForm(profile: original).profile }
    private var original: AccountProfile { AccountProfile(name: name, job: job, introduction: introduction, contacts: [], histories: [], version: version) }

    private static func formatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }
    static func day(_ date: Date) -> String { formatter().string(from: date) }
    static func date(_ text: String) -> Date? { formatter().date(from: String(text.prefix(10))) }
}
