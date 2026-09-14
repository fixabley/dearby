import Foundation

struct ActivitySchedule: Decodable {
    let phase: String
    let startsOn: String?
    let startsAt: String?
    let endsAt: String?
    let endsOn: String?
    let timezone: String?
    let mode: String?
    let onlineUrl: String?

    var label: String {
        return ["event": "행사", "preliminary": "예선", "finalist_announcement": "결선 진출 발표", "final": "결선·시상"][phase] ?? phase
    }

    var summary: String {
        // The contract records source-local ISO timestamps with an explicit offset.
        let start = startsAt.map { String($0.prefix(16)).replacingOccurrences(of: "T", with: " ") } ?? startsOn ?? "일정 미확인"
        let end = endsAt.map { " ~ " + String($0.prefix(16)).replacingOccurrences(of: "T", with: " ") } ?? ""
        return "\(label): \(start)\(end) (한국 시간)"
    }
}
