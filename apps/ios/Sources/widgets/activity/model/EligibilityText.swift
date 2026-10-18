import Foundation

/// Korean chip texts for an activity's eligibility (contract "대상자 조건"): ranges read as `나이 19~34세`, sets as
/// `재학상태 재학`, `any` groups join with ` 또는 `, `기타` shows its note. Android and web mirror these rules.
enum EligibilityText {
    static func chips(_ eligibility: Eligibility) -> [String] {
        switch eligibility.op {
        case .all: return eligibility.items.flatMap(chips)
        case .any:
            let joined = eligibility.items.flatMap(chips).joined(separator: " 또는 ")
            return joined.isEmpty ? [] : [joined]
        }
    }
    private static func chips(_ item: Eligibility.Item) -> [String] {
        switch item {
        case .group(let group): return chips(group)
        case .condition(let condition): return text(condition).map { [$0] } ?? []
        }
    }
    static func text(_ condition: Eligibility.Condition) -> String? {
        let from = condition.from.map(number), to = condition.to.map(number)
        switch condition.field {
        case "기타": return condition.note ?? nil
        case "나이": return range(from, to, both: { "나이 \($0)~\($1)세" }, lower: { "\($0)세 이상" }, upper: { "\($0)세 이하" })
        case "경력연수": return range(from, to, both: { "경력 \($0)~\($1)년" }, lower: { "경력 \($0)년 이상" }, upper: { "경력 \($0)년 이하" })
        case "학년":
            if let from, let to, from == to { return "\(from)학년" }
            return range(from, to, both: { "\($0)~\($1)학년" }, lower: { "\($0)학년 이상" }, upper: { "\($0)학년 이하" })
        case "출생연도": return range(from, to, both: { "\($0)~\($1)년 출생" }, lower: { "\($0)년 이후 출생" }, upper: { "\($0)년 이전 출생" })
        case "팀규모": return range(from, to, both: { "\($0)~\($1)명" }, lower: { "\($0)명 이상" }, upper: { "\($0)명 이하" })
        default:
            if !condition.values.isEmpty { return "\(condition.field) \(condition.values.joined(separator: "·"))" }
            if let numeric = range(from, to, both: { "\($0)~\($1)" }, lower: { "\($0) 이상" }, upper: { "\($0) 이하" }) {
                return "\(condition.field) \(numeric)"
            }
            return condition.note ?? condition.field
        }
    }
    private static func range(_ from: String?, _ to: String?, both: (String, String) -> String,
                              lower: (String) -> String, upper: (String) -> String) -> String? {
        switch (from, to) {
        case let (from?, to?): return both(from, to)
        case let (from?, nil): return lower(from)
        case let (nil, to?): return upper(to)
        case (nil, nil): return nil
        }
    }
    /// Whole numbers without a decimal point; anything else as written.
    private static func number(_ value: Double) -> String {
        value == value.rounded() && abs(value) < 1e15 ? String(Int(value)) : String(value)
    }
}
