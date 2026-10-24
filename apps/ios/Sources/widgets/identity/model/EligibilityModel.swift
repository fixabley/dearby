import Foundation
import Observation

/// 지원 자격 정보 (contract "추천 설정 퍼널·지원 자격 정보·추천" 2026-10-24): `GET/PUT/DELETE /v1/me/eligibility-profile`, entered after the
/// consent sheet and only signed in. Values are never logged. A server without the endpoint (404) hides the row.
@MainActor @Observable final class EligibilityModel {
    enum Phase: Equatable { case signedOut, loading, loaded(AccountClient.EligibilityProfile), unavailable, failed }
    private(set) var phase = Phase.loading
    let account: AccountViewModel
    init(account: AccountViewModel) { self.account = account }
    var profile: AccountClient.EligibilityProfile? { if case .loaded(let profile) = phase { profile } else { nil } }
    /// Something is entered (the consent record alone does not count).
    var hasValues: Bool { profile.map { EligibilityStatus($0) != nil || $0.birthYear != nil } ?? false }
    /// No consent yet, or one to an older version: the sheet comes first.
    var needsConsent: Bool { profile?.consent?.version != ConsentVersions.eligibility }
    /// `재학 중 · 3학년 · 2003년생`.
    var summary: String? {
        guard let profile, hasValues else { return nil }
        let status = EligibilityStatus(profile)
        return [status?.label, status?.hasGrade == true ? profile.schoolYear.map(EligibilityStatus.gradeLabel) : nil,
                profile.birthYear.map { "\(String($0))년생" }].compactMap { $0 }.joined(separator: " · ")
    }
    func load() async {
        guard account.session != nil else { phase = .signedOut; return }
        if profile == nil { phase = .loading }
        do {
            phase = .loaded(try await account.authorized { [client = account.client] in try await client.eligibilityProfile($0) })
        } catch AccountError.notFound {
            phase = .unavailable
        } catch AccountError.unauthorized {
            phase = .signedOut
        } catch {
            if profile == nil { phase = .failed }
        }
    }
    /// Saves with the current consent version; returns a message to show, or nil when saved.
    func save(status: EligibilityStatus, schoolYear: Int?, birthYear: Int) async -> String? {
        let input = AccountClient.EligibilityInput(consentVersion: ConsentVersions.eligibility, educationStatus: status.educationStatus,
                                                   employment: status.employment, schoolYear: status.hasGrade ? schoolYear : nil, birthYear: birthYear)
        do {
            phase = .loaded(try await account.authorized { [client = account.client] in try await client.saveEligibilityProfile(input, $0) })
            return nil
        } catch { return Self.message(error, fallback: "지원 자격 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    /// 지원 자격 정보 지우기: values and consent go together.
    func delete() async -> String? {
        do {
            try await account.authorized { [client = account.client] in try await client.deleteEligibilityProfile($0) }
            phase = .loaded(.empty)
            return nil
        } catch { return Self.message(error, fallback: "지원 자격 정보를 지우지 못했어요. 잠시 후 다시 시도해 주세요.") }
    }
    private static func message(_ error: Error, fallback: String) -> String {
        switch error as? AccountError {
        case .unauthorized: "로그인이 만료됐어요. 다시 로그인해 주세요."
        case .rejected(let detail): detail
        default: fallback
        }
    }
}

/// 지금 상황: one choice, stored with the eligibility vocabulary (contract "대상자 조건"; the same mapping as the server and Android): 재학·휴학·
/// 졸업 예정 are that `educationStatus` with `employment: student`, 졸업 is `graduated` alone, 직장 is `employment: employed` alone.
enum EligibilityStatus: CaseIterable, Sendable {
    case enrolled, onLeave, graduating, graduated, employed
    var label: String {
        switch self {
        case .enrolled: "재학 중"
        case .onLeave: "휴학 중"
        case .graduating: "졸업 예정"
        case .graduated: "졸업"
        case .employed: "직장"
        }
    }
    /// The question's wording; `label` is the short form in the row's summary.
    var option: String {
        switch self {
        case .graduated: "졸업했어요"
        case .employed: "직장에 다녀요"
        default: label
        }
    }
    /// 학년 is asked only while still at school.
    var hasGrade: Bool { [.enrolled, .onLeave, .graduating].contains(self) }
    var educationStatus: String? {
        switch self {
        case .enrolled: "enrolled"
        case .onLeave: "onLeave"
        case .graduating: "graduating"
        case .graduated: "graduated"
        case .employed: nil
        }
    }
    var employment: String? { self == .employed ? "employed" : hasGrade ? "student" : nil }
    init?(_ profile: AccountClient.EligibilityProfile) {
        if profile.employment == "employed" { self = .employed; return }
        guard let status = Self.allCases.first(where: { $0.educationStatus != nil && $0.educationStatus == profile.educationStatus }) else { return nil }
        self = status
    }
    /// 1~5, 5 meaning 5학년 이상.
    static func gradeLabel(_ year: Int) -> String { year >= 5 ? "5학년 이상" : "\(year)학년" }
}
