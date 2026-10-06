import Foundation

/// The 내 프로필 edit form (contract #149): required phone and email, other contacts added by kind, and history
/// periods picked as dates. Checks and messages live here; the widgets only show them.
struct ProfileForm: Equatable {
    struct History: Equatable, Identifiable {
        let id: String
        var title: String
        var role: String
        var start: Date?
        var end: Date?
        var ongoing: Bool
        var description: String
    }
    var name: String
    var job: String
    var introduction: String
    var phone: String
    var email: String
    var extras: [ProfileExtraContact]
    var histories: [History]
    private var phoneID: String
    private var emailID: String

    /// The saved profile; an empty email is filled with the sign-in address.
    init(profile: AccountProfile, signInEmail: String = "") {
        name = profile.name
        job = profile.job
        introduction = profile.introduction
        let phoneRow = profile.contacts.first { $0.kind == "phone" }, emailRow = profile.contacts.first { $0.kind == "email" }
        phone = phoneRow?.value ?? ""
        email = emailRow?.value ?? signInEmail
        phoneID = phoneRow?.id ?? UUID().uuidString.lowercased()
        emailID = emailRow?.id ?? UUID().uuidString.lowercased()
        extras = profile.contacts.filter { $0.id != phoneRow?.id && $0.id != emailRow?.id }
            .map { ProfileExtraContact(id: $0.id, kind: $0.kind, value: $0.value) }
        histories = profile.histories.map {
            History(id: $0.id, title: $0.title, role: $0.role, start: Self.date($0.startDate), end: $0.endDate.flatMap(Self.date),
                    ongoing: $0.endDate == nil, description: $0.description)
        }
    }
    mutating func addContact(_ kind: String) { extras.append(ProfileExtraContact(id: UUID().uuidString.lowercased(), kind: kind, value: "")) }
    mutating func addHistory() {
        histories.append(History(id: UUID().uuidString.lowercased(), title: "", role: "", start: nil, end: nil, ongoing: true, description: ""))
    }

    var nameError: String? { name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "이름을 입력해 주세요." : nil }
    var phoneError: String? {
        if phone.trimmingCharacters(in: .whitespaces).isEmpty { return "전화번호를 입력해 주세요." }
        return ContactRules.validPhone(phone) ? nil : "전화번호는 숫자 8~15자리로 입력해 주세요."
    }
    var emailError: String? {
        if email.trimmingCharacters(in: .whitespaces).isEmpty { return "이메일을 입력해 주세요." }
        return ContactRules.validEmail(email) ? nil : "이메일 주소를 확인해 주세요."
    }
    func historyError(_ history: History) -> String? {
        if history.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "활동 이름을 입력해 주세요." }
        guard let start = history.start else { return "시작일을 골라 주세요." }
        if !history.ongoing, let end = history.end, end < start { return "종료일은 시작일 이후로 골라 주세요." }
        return nil
    }
    var isValid: Bool { nameError == nil && phoneError == nil && emailError == nil && histories.allSatisfy { historyError($0) == nil } }

    /// `PUT /v1/profile` body. Empty added contacts are left out; dates are saved as picked (`YYYY-MM-DD`).
    var profile: AccountProfile {
        let labels = Dictionary(uniqueKeysWithValues: ProfileContactFields.kinds.map { ($0.kind, $0.label) })
        let trimmed = { (text: String) in text.trimmingCharacters(in: .whitespacesAndNewlines) }
        return AccountProfile(
            name: trimmed(name), job: trimmed(job), introduction: trimmed(introduction),
            contacts: [AccountContact(id: phoneID, kind: "phone", label: "전화번호", value: trimmed(phone)),
                       AccountContact(id: emailID, kind: "email", label: "이메일", value: trimmed(email))]
                + extras.filter { !trimmed($0.value).isEmpty }
                    .map { AccountContact(id: $0.id, kind: $0.kind, label: labels[$0.kind] ?? $0.kind, value: trimmed($0.value)) },
            histories: histories.map {
                AccountHistory(id: $0.id, title: trimmed($0.title), role: trimmed($0.role), startDate: $0.start.map(Self.day) ?? "",
                               endDate: $0.ongoing ? nil : $0.end.map(Self.day), description: $0.description)
            })
    }
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
