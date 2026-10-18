import Foundation

/// One history being entered step by step (contract "이력", 2026-10-18 funnel split):
/// 조직 이력은 조직 → 역할 → 기간 → 설명, 활동 이력은 활동 → (직접 입력일 때만 조직 → 프로그램) → 역할 → 기간 → 설명.
/// Each catalogue step holds either a picked `ActivityInfoRef` or a typed name; typed names wait for admin approval.
struct HistoryDraft: Equatable, Identifiable {
    enum Step: Int, CaseIterable { case kind, organization, program, activity, role, period, description, summary }
    let id: String
    var kind = "organization"
    var organization: ActivityInfoRef?
    var organizationName = ""
    var program: ActivityInfoRef?
    var programName = ""
    var activity: ActivityInfoRef?
    var activityName = ""
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
        id = h.id; kind = h.kind
        organization = h.organization; organizationName = h.organization == nil ? (h.organizationName ?? "") : ""
        program = h.program; programName = h.program == nil ? (h.programName ?? "") : ""
        activity = h.activity; activityName = h.activity == nil ? (h.activityName ?? "") : ""
        role = h.role; roleName = h.role == nil ? (h.roleName ?? "") : ""
        start = ProfileForm.date(h.startDate); end = h.endDate.flatMap(ProfileForm.date); ongoing = h.endDate == nil
        description = h.description
        approval = h.approval; approvalReason = h.approvalReason; verification = h.verification; evidence = h.evidence
    }
    var isActivity: Bool { kind == "activity" }
    /// An activity typed by hand (not picked from 활동 정보): the server cannot derive its organization, so the funnel asks.
    var isCustomActivity: Bool { isActivity && activity == nil && !activityName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    /// Steps in order for this kind. A picked activity carries its organization and program, so they are not asked.
    var steps: [Step] {
        let middle: [Step] = isActivity ? ([.activity] + (isCustomActivity ? [.organization, .program] : [])) : [.organization]
        return [.kind] + middle + [.role, .period, .description, .summary]
    }
    var history: AccountHistory {
        let trimmed = { (text: String) in text.trimmingCharacters(in: .whitespacesAndNewlines) }
        let name = { (text: String) -> String? in trimmed(text).isEmpty ? nil : trimmed(text) }
        return AccountHistory(
            id: id, kind: kind,
            organization: organization, organizationName: organization == nil ? name(organizationName) : nil,
            program: program, programName: program == nil ? name(programName) : nil,
            activity: isActivity ? activity : nil, activityName: isActivity && activity == nil ? name(activityName) : nil,
            role: role, roleName: role == nil ? name(roleName) : nil,
            startDate: start.map(ProfileForm.day) ?? "", endDate: ongoing ? nil : end.map(ProfileForm.day),
            description: trimmed(description), approval: approval, approvalReason: approvalReason, verification: verification, evidence: evidence)
    }
    /// Why a step cannot be left yet, or nil.
    func error(at step: Step) -> String? {
        let blank = { (text: String) in text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        switch step {
        case .organization: return steps.contains(.organization) && organization == nil && blank(organizationName) ? "조직을 고르거나 적어 주세요." : nil
        case .activity: return isActivity && activity == nil && blank(activityName) ? "활동을 고르거나 적어 주세요." : nil
        case .role: return role == nil && blank(roleName) ? "역할을 고르거나 적어 주세요." : nil
        case .period:
            guard let start else { return "시작일을 골라 주세요." }
            if !ongoing, let end, end < start { return "종료일은 시작일 이후로 골라 주세요." }
            return nil
        default: return nil
        }
    }
    var isValid: Bool { steps.allSatisfy { error(at: $0) == nil } }
    var chain: [String] { history.chain }
    var roleTitle: String { history.roleTitle }
    /// `조직 활동 · 2026.03 – 진행 중`
    var summaryLine: String {
        (isActivity ? "활동 참여" : "조직 활동") + " · " + HistoryPeriodFields.period(start: start, end: ongoing ? nil : end, ongoing: ongoing)
    }
}
