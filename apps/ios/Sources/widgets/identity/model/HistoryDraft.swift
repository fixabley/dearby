import Foundation

/// One history being entered step by step (contract "이력 (History) — 역할 매칭", 2026-11-03 one funnel):
/// 어디에서(조직·프로그램·활동 하나, 없으면 종류를 고르고 이름 직접 입력) → 역할(그 단위의 역할, 없으면 직접 입력) → 기간 → 설명.
/// A picked unit is `owner` (registered); a typed unit is `ownerKind` + `ownerName`. Typed values wait for admin approval.
struct HistoryDraft: Equatable, Identifiable {
    enum Step: Int, CaseIterable { case owner, role, period, description, summary }
    let id: String
    var owner: HistoryOwner?
    var ownerKind = "organization"
    var ownerName = ""
    /// The unit and its parents as refs (the server's convenience values, or the picked hit's), passed through so an untouched saved
    /// history round-trips unchanged and a new row shows its 소속 line before the save.
    var organization: ActivityInfoRef?
    var program: ActivityInfoRef?
    var activity: ActivityInfoRef?
    var role: ActivityInfoRef?
    var roleName = ""
    var start: Date?
    var end: Date?
    var ongoing = true
    var description = ""
    // Read-only server state, kept so edits do not lose it.
    var approval = "approved"
    var approvalReason: String?
    var verification = "unverified"
    var evidence: [EvidenceMeta] = []

    init() { id = UUID().uuidString.lowercased() }
    init(history h: AccountHistory) {
        id = h.id
        if h.owner.id != nil { owner = h.owner } else { ownerKind = h.owner.kind; ownerName = h.owner.name }
        organization = h.organization; program = h.program; activity = h.activity
        role = h.role; roleName = h.role == nil ? h.roleName : ""
        start = ProfileForm.date(h.startDate); end = h.endDate.flatMap(ProfileForm.date); ongoing = h.endDate == nil
        description = h.description
        approval = h.approval; approvalReason = h.approvalReason; verification = h.verification; evidence = h.evidence
    }
    private static func trimmed(_ text: String) -> String { text.trimmingCharacters(in: .whitespacesAndNewlines) }
    /// The unit as it will be saved: the picked one, or the typed kind and name.
    var unit: HistoryOwner { owner ?? HistoryOwner(kind: ownerKind, id: nil, name: Self.trimmed(ownerName)) }
    /// A picked search hit: the unit becomes `owner` and the refs follow the server's rule (an activity's 조직·프로그램·활동, a program's 조직·프로그램, an organization's 조직).
    mutating func pick(_ item: ActivityInfoItem) {
        let kind = item.type ?? "activity", ref = ActivityInfoRef(id: item.id, name: item.name)
        owner = HistoryOwner(kind: kind, id: item.id, name: item.name)
        organization = kind == "organization" ? ref : item.organization
        program = kind == "program" ? ref : item.program
        activity = kind == "activity" ? ref : nil
    }
    var history: AccountHistory {
        AccountHistory(
            id: id, role: role, roleName: role?.name ?? Self.trimmed(roleName), owner: unit, organization: organization, program: program, activity: activity,
            startDate: start.map(ProfileForm.day) ?? "", endDate: ongoing ? nil : end.map(ProfileForm.day),
            description: Self.trimmed(description), approval: approval, approvalReason: approvalReason, verification: verification, evidence: evidence)
    }
    /// Why a step cannot be left yet, or nil.
    func error(at step: Step) -> String? {
        switch step {
        case .owner: return owner == nil && Self.trimmed(ownerName).isEmpty ? "조직·프로그램·활동을 고르거나 적어 주세요." : nil
        case .role: return role == nil && Self.trimmed(roleName).isEmpty ? "역할을 고르거나 적어 주세요." : nil
        case .period:
            guard let start else { return "시작일을 골라 주세요." }
            if !ongoing, let end, end < start { return "종료일은 시작일 이후로 골라 주세요." }
            return nil
        default: return nil
        }
    }
    /// `활동 · 2026.03 – 진행 중`
    var summaryLine: String { unit.kindName + " · " + HistoryPeriodFields.period(start: start, end: ongoing ? nil : end, ongoing: ongoing) }
}
